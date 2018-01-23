import org.junit.Before;
import org.junit.Test;

import java.security.*;

import static org.junit.Assert.assertTrue;

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
    public void isValidTx() {
        // given
        Transaction tx = new TransactionBuilder().addInput(new byte[]{0x1}, 0, alice_pr)
                .addOutput(0.5d, bob_p).addOutput(0.4d, tom_p).build();
        // when //then
        assertTrue(handler.isValidTx(tx));
    }

    private Transaction.Output out(double value, PublicKey person) {
        return new TransactionBuilder().addOutput(value, person).build().getOutput(0);
    }

    private UTXO utxo(int index, byte[] hash) {
        return new UTXO(hash, index);
    }

    public class TransactionBuilder {
        private final Transaction tx;

        public TransactionBuilder() {
            tx = new Transaction();
        }

        public TransactionBuilder addInput(byte[] prevHash, int index, PrivateKey key) {
            tx.addInput(prevHash, index);
            try {
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(key);
                tx.addSignature(signature.sign(), tx.getInputs().size() - 1);
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
                e.printStackTrace();
            }
            return this;
        }

        public TransactionBuilder addOutput(double value, PublicKey person) {
            tx.addOutput(value, person);
            return this;
        }

        public Transaction build() {
            tx.setHash(tx.getRawTx());
            return tx;
        }
    }
}
