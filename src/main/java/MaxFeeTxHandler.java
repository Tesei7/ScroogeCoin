public class MaxFeeTxHandler extends TxHandler {
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     *
     * @param utxoPool
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        super(utxoPool);
    }

    @Override
    public double getWeightOfTx(TxHandler.TxNode txNode) {
        Double sumInput = txNode.tx.getInputs().stream().mapToDouble(
                input -> verificator.getCorrespondingOutput(txNode.tx, input).value).sum();
        Double sumOutput = txNode.tx.getOutputs().stream().mapToDouble(output -> output.value).sum();
        return sumInput - sumOutput;
    }
}
