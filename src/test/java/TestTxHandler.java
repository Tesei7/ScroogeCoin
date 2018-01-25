import org.junit.Before;
import org.junit.Test;

import java.security.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class TestTxHandler {
    private TxHandler handler;
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
        handler = new TxHandler(utxoPool);
    }

    @Test
    public void shouldBeValidTx() {
        // given
        Transaction tx = new TransactionBuilder().addInput(new byte[]{0x1}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(0.4d, tom_p).build();
        // when //then
        assertTrue(handler.isValidTx(tx));
    }

    @Test
    public void incorrectInputCoin() {
        // given
        Transaction tx = new TransactionBuilder().addInput(new byte[]{0x11}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(0.4d, tom_p).build();
        // when //then
        assertFalse(handler.isValidTx(tx));
    }

    @Test
    public void incorrectInputSign() {
        // given
        Transaction tx = new TransactionBuilder().addInput(new byte[]{0x1}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(0.4d, tom_p).build();
        tx.getInput(0).signature = new byte[]{0x1};
        // when //then
        assertFalse(handler.isValidTx(tx));
    }

    @Test
    public void sameInputs() {
        // given
        Transaction tx = new TransactionBuilder()
                .addInput(new byte[]{0x1}, 0, alice_pr).addInput(new byte[]{0x1}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(0.4d, tom_p).build();
        // when //then
        assertFalse(handler.isValidTx(tx));
    }

    @Test
    public void negativeOutput() {
        // given
        Transaction tx = new TransactionBuilder().addInput(new byte[]{0x1}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(-0.4d, tom_p).build();
        // when //then
        assertFalse(handler.isValidTx(tx));
    }

    @Test
    public void outputsGreaterThanInputs() {
        // given
        Transaction tx = new TransactionBuilder().addInput(new byte[]{0x1}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(0.6d, tom_p).build();
        // when //then
        assertFalse(handler.isValidTx(tx));
    }

    private Transaction.Output out(double value, PublicKey person) {
        return new TransactionBuilder().addOutput(value, person).build().getOutput(0);
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

        public TransactionBuilder addInput(byte[] prevHash, int index, PrivateKey key) {
            tx.addInput(prevHash, index);
            privateKeys.add(key);
            return this;
        }

        public TransactionBuilder addOutput(double value, PublicKey person) {
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
