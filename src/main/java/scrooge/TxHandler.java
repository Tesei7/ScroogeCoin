package scrooge;

import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

public class TxHandler {

    private UTXOPool unspentCoins;
    private TransactionVerificator verificator;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        unspentCoins = new UTXOPool(utxoPool);
        verificator = new TransactionVerificator();
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        return verificator.allTxInputsInPool(tx) &&
                verificator.allTxSignsCorrect(tx) &&
                verificator.allTxInputsDifferent(tx) &&
                verificator.allOutputsAreNonNegative(tx) &&
                verificator.inputsGreaterOrEqualOutputs(tx);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Set<Transaction> performedTxs = new HashSet<>();
        Set<CoinNode> initialCoins = unspentCoins.getAllUTXO().stream().map(CoinNode::new).collect(Collectors.toSet());

        HashMap<Transaction, TxNode> txs = new HashMap<>();
        recursiveFillTxTree(initialCoins, possibleTxs, txs);

        initialCoins.forEach(c->c.execute(performedTxs));
        return performedTxs.toArray(new Transaction[0]);
    }

    private void recursiveFillTxTree(Set<CoinNode> coinNodes, Transaction[] possibleTxs, HashMap<Transaction, TxNode> txs) {
        Set<CoinNode> newCoins = new HashSet<>();
        coinNodes.forEach(coinNode -> {
            Arrays.stream(possibleTxs).forEach(tx -> {
                tx.getInputs().forEach(input -> {
                    if (coinNode.utxo.equals(verificator.getUtxo(tx, input))) {
                        TxNode txNode;
                        if (txs.get(tx) != null) {
                            txNode = txs.get(tx);
                        } else {
                            txNode = new TxNode(tx);
                            txs.put(tx, txNode);
                        }
                        coinNode.txs.add(txNode);
                        newCoins.addAll(txNode.coins);
                    }
                });
            });
        });

        if (!newCoins.isEmpty()) {
            recursiveFillTxTree(newCoins, possibleTxs, txs);
        }
    }

    private class TransactionVerificator {
        private boolean allTxInputsInPool(Transaction tx) {
            for (int i = 0; i < tx.getInputs().size(); i++) {
                Transaction.Input input = tx.getInputs().get(i);
                UTXO utxo = new UTXO(tx.getHash(), input.outputIndex);
                if (!unspentCoins.contains(utxo)) return false;
            }
            return true;
        }

        private boolean allTxSignsCorrect(Transaction tx) {
            for (int i = 0; i < tx.getInputs().size(); i++) {
                Transaction.Input input = tx.getInputs().get(i);
                Transaction.Output output = getCorrespondingOutput(tx, input);
                if (output == null) return false;

                PublicKey publicKey = output.address;
                if (!Crypto.verifySignature(publicKey, tx.getRawDataToSign(i), input.signature)) return false;
            }
            return true;
        }

        private boolean allTxInputsDifferent(Transaction tx) {
            Set<UTXO> txInputs = new HashSet<>();
            for (int i = 0; i < tx.getInputs().size(); i++) {
                Transaction.Input input = tx.getInputs().get(i);
                txInputs.add(getUtxo(tx, input));
            }
            return txInputs.size() == tx.getInputs().size();
        }

        private boolean allOutputsAreNonNegative(Transaction tx) {
            return tx.getOutputs().stream().noneMatch(output -> output.value < 0);
        }

        private boolean inputsGreaterOrEqualOutputs(Transaction tx) {
            Double sumInput = tx.getInputs().stream().mapToDouble(input -> getCorrespondingOutput(tx, input).value).sum();
            Double sumOutput = tx.getOutputs().stream().mapToDouble(output -> output.value).sum();
            return sumInput >= sumOutput;
        }

        private Transaction.Output getCorrespondingOutput(Transaction tx, Transaction.Input input) {
            return unspentCoins.getTxOutput(getUtxo(tx, input));
        }

        public UTXO getUtxo(Transaction tx, Transaction.Input input) {
            return new UTXO(tx.getHash(), input.outputIndex);
        }
    }

    private class TxNode {
        public Transaction tx;
        public boolean isPerformed = false;
        public List<CoinNode> coins = new ArrayList<>();

        public TxNode(Transaction tx) {
            this.tx = tx;
            for (int i = 0; i < tx.getOutputs().size(); i++) {
                coins.add(new CoinNode(new UTXO(tx.getHash(), i)));
            }
        }

        public void performTx(Set<Transaction> performed) {
            if (isPerformed || !isValidTx(tx)) return;
            for (Transaction.Input in : tx.getInputs()) {
                unspentCoins.removeUTXO(verificator.getUtxo(tx, in));
            }
            coins.forEach(c -> unspentCoins.removeUTXO(c.utxo));
            isPerformed = true;
            performed.add(tx);

            coins.forEach(coinNode -> coinNode.execute(performed));
        }

        public int getWeight() {
            int weight = 0;
            for (CoinNode coin : coins) {
                for (TxNode tx : coin.txs) {
                    weight += tx.getWeight();
                }
            }
            return weight;
        }
    }

    private class CoinNode {
        public UTXO utxo;
        public List<TxNode> txs = new ArrayList<>();

        public CoinNode(UTXO utxo) {
            this.utxo = utxo;
        }

        public void execute(Set<Transaction> performed) {
            Optional<TxNode> max = txs.stream().max(Comparator.comparing(TxNode::getWeight));
            max.ifPresent(txNode -> txNode.performTx(performed));
        }

        public void resolveDoubleSpending() {

        }
    }
}
