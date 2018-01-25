import org.junit.Before;
import org.junit.Test;

import java.security.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestMaxFeeTxHandler {

    private MaxFeeTxHandler handler;
    private UTXOPool utxoPool;

    private PublicKey bob_p;
    private PublicKey alice_p;
    private PublicKey tom_p;

    private PrivateKey bob_pr;
    private PrivateKey alice_pr;
    private PrivateKey tom_pr;

    @Before
    public void setUp() throws NoSuchProviderException, NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        bob_p = keyPair.getPublic();
        bob_pr = keyPair.getPrivate();
        keyPair = keyPairGenerator.generateKeyPair();
        alice_p = keyPair.getPublic();
        alice_pr = keyPair.getPrivate();
        keyPair = keyPairGenerator.generateKeyPair();
        tom_p = keyPair.getPublic();
        tom_pr = keyPair.getPrivate();

        utxoPool = new UTXOPool();
        utxoPool.addUTXO(utxo(0, new byte[]{0x1}), out(1d, alice_p));
        utxoPool.addUTXO(utxo(0, new byte[]{0x2}), out(2d, bob_p));
        utxoPool.addUTXO(utxo(0, new byte[]{0x3}), out(3d, tom_p));
        handler = new MaxFeeTxHandler(utxoPool);
    }

    @Test
    public void shouldVerifyCorrectly() {
        // given
        List<Transaction> txs = new ArrayList<>();
        Transaction tx1 = new TestMaxFeeTxHandler.TransactionBuilder().in(new byte[]{0x1}, 0, alice_pr).out(0.5d, bob_p).out(0.4d, tom_p).build();
        txs.add(tx1);
        Transaction tx2 = new TestMaxFeeTxHandler.TransactionBuilder().in(tx1.getHash(), 0, bob_pr).out(0.4d, tom_p).build();
        txs.add(tx2);
        Transaction tx3 = new TestMaxFeeTxHandler.TransactionBuilder().in(new byte[]{0x3}, 0, tom_pr).out(2.9d, alice_p).build();
        txs.add(tx3);
        Transaction tx4 = new TestMaxFeeTxHandler.TransactionBuilder().in(new byte[]{0x1}, 0, alice_pr).out(0.8d, bob_p).build();
        txs.add(tx4);
        // when
        Transaction[] handledTxs = handler.handleTxs(txs.toArray(new Transaction[0]));
        // then
        UTXOPool unspentCoins = handler.getUnspentCoins();
        assertEquals(2, handledTxs.length);
        assertTrue(unspentCoins.contains(utxo(0, new byte[]{0x2})));
        assertTrue(unspentCoins.contains(utxo(0, tx3.getHash())));
        assertTrue(unspentCoins.contains(utxo(0, tx4.getHash())));
        assertEquals(3, unspentCoins.getAllUTXO().size());
    }

    private Transaction.Output out(double value, PublicKey person) {
        return new TestMaxFeeTxHandler.TransactionBuilder().out(value, person).build().getOutput(0);
    }

    private UTXO utxo(int index, byte[] hash) {
        return new UTXO(hash, index);
    }

    public class TransactionBuilder {
        private final Transaction tx;
        private List<PrivateKey> privateKeys;

        public TransactionBuilder() {
            tx = new Transaction();
            privateKeys = new ArrayList<>();
        }

        public TestMaxFeeTxHandler.TransactionBuilder in(byte[] prevHash, int index, PrivateKey key) {
            tx.addInput(prevHash, index);
            privateKeys.add(key);
            return this;
        }

        public TestMaxFeeTxHandler.TransactionBuilder out(double value, PublicKey person) {
            tx.addOutput(value, person);
            return this;
        }

        public Transaction build() {
            tx.setHash(tx.getRawTx());
            signInputs();
            return tx;
        }

        private void signInputs() {
            try {
                for (int i = 0; i < tx.getInputs().size(); i++) {
                    Signature signature = Signature.getInstance("SHA256withRSA");
                    signature.initSign(privateKeys.get(i));
                    signature.update(tx.getRawDataToSign(i));
                    tx.addSignature(signature.sign(), i);
                }
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
                e.printStackTrace();
            }
        }
    }
}
