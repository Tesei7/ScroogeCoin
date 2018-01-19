package scrooge;

import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

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
        return verificator.allTxOutputsInPool(tx) &&
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
        // IMPLEMENT THIS
    }

    private class TransactionVerificator {
        private boolean allTxOutputsInPool(Transaction tx) {
            for (int i = 0; i < tx.getOutputs().size(); i++) {
                UTXO utxo = new UTXO(tx.getHash(), i);
                if (!unspentCoins.contains(utxo)) return false;
            }
            return true;
        }

        private boolean allTxSignsCorrect(Transaction tx) {
            for (int i = 0; i < tx.getInputs().size(); i++) {
                Transaction.Input input = tx.getInputs().get(i);
                UTXO utxo = new UTXO(tx.getHash(), input.outputIndex);
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
                txInputs.add(new UTXO(tx.getHash(), input.outputIndex));
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
            UTXO utxo = new UTXO(tx.getHash(), input.outputIndex);
            return unspentCoins.getTxOutput(utxo);
        }
    }
}
