package assignment3starterCode;

import java.security.PublicKey;
import java.util.*;

public class TxHandler {

    private UTXOPool utxoPool;

    private UTXOPool claimedOutputs;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
        this.claimedOutputs = new UTXOPool();
    }

    /**
     * Returns copy of utxoPool
     */
    public UTXOPool getUTXOPool(){
        return utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
//        UTXOPool claimedOutputs = this.claimedOutputs;

        //loop over tx's claimed inputs, return false if any are not in UTXO pool
        //return false if input signature is invalid
        ArrayList<Transaction.Input> txInputs = tx.getInputs();
        UTXOPool locallyClaimedInputs = new UTXOPool();
        double inputValue = 0;
        for (int i = 0; i < txInputs.size(); i++){
            Transaction.Input ip = tx.getInput(i);
            UTXO utxo = new UTXO(ip.prevTxHash, ip.outputIndex);
            //Check if input is in utxoPool and not in claimed pools
            if (utxoPool.contains(utxo) && !claimedOutputs.contains(utxo)
                    && !locallyClaimedInputs.contains(utxo)){
                //Check if input signature is valid, add input to locallyClaimedInputs
                //add input value to inputValue
                PublicKey pubKey = utxoPool.getTxOutput(utxo).address;
                byte[] message = tx.getRawDataToSign(i);
                if (Crypto.verifySignature(pubKey, message, ip.signature)){
                    locallyClaimedInputs.addUTXO(utxo, utxoPool.getTxOutput(utxo));
                    inputValue += utxoPool.getTxOutput(utxo).value;
                }
                //Else, input signature is invalid or , return false
                else{
                    return false;
                }
            }
            //Else, input is not in utxoPool or has already been claimed, return false
            else{
                return false;
            }
        }

        //loop through output values, return false if negative value encountered
        //sum values of all outputs
        ArrayList<Transaction.Output> txOutputs = tx.getOutputs();
        double outputValue = 0;
        for (Transaction.Output op : txOutputs){
            if (op.value < 0){
                return false;
            }
            else{
                outputValue += op.value;
            }
        }

        //return false if sum of outputs is greater than sum of inputs
        if (inputValue < outputValue){
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        claimedOutputs = new UTXOPool();
        //Create final list for mutually valid txs to be returned
        List<Transaction> finalTxsList = new ArrayList<>();

        //Loop through all txs, adding valid txs to finalTxsList
        for (Transaction tx : possibleTxs){
            //check each tx for validity w isValidTx, and add to final list if valid
            //add all outputs of valid txs to utxoPool
            if (isValidTx(tx)){
                finalTxsList.add(tx);

                //remove valid tx's claimed outputs from utxoPool
                ArrayList<Transaction.Input> inputs = tx.getInputs();
                for (Transaction.Input ip : inputs){
                    UTXO utxo = new UTXO(ip.prevTxHash, ip.outputIndex);
                    utxoPool.removeUTXO(utxo);
                }

                //add valid tx's new outputs to utxoPool
                ArrayList<Transaction.Output> outputs = tx.getOutputs();
                int i = 0;
                for (Transaction.Output op : outputs){
                    utxoPool.addUTXO(new UTXO(tx.getHash(),i), op);
                    i++;
                }
            }
        }

        //Return finalTxsList as Transaction[]
        return finalTxsList.toArray(new Transaction[0]);
    }
}
