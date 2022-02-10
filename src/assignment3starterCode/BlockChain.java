package assignment3starterCode;// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.sql.Timestamp;
import java.util.ArrayList;

public class BlockChain {

    public class BlockNode {

        public Block block;
        public int height;
        public UTXOPool utxoPool;
        public TransactionPool txPool;
        public Timestamp timeCreated;

        public BlockNode(Block block, int height, UTXOPool utxoPool, TransactionPool txPool){
            this.block = block;
            this.height = height;
            this.utxoPool = utxoPool;
            this.txPool = txPool;
            timeCreated = new Timestamp(System.currentTimeMillis());
        }

        public UTXOPool getUTXOPool(){
            return this.utxoPool;
        }

        public TransactionPool getTransactionPool() {
            return this.txPool;
        }
    }

    public static final int CUT_OFF_AGE = 10;
    private ArrayList<BlockNode> blockChainList = new ArrayList<>();
    private BlockNode maxHeightBlockNode;
    private TransactionPool txPool = new TransactionPool();

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool genutxoPool = new UTXOPool();
        TransactionPool txPool = new TransactionPool();
        for (int i = 0; i < genesisBlock.getCoinbase().numOutputs(); i++) {
            genutxoPool.addUTXO(new UTXO(genesisBlock.getCoinbase().getHash(),i),genesisBlock.getCoinbase().getOutput(i));
        }
        txPool.addTransaction(genesisBlock.getCoinbase());
        for (Transaction t : genesisBlock.getTransactions()) {
            if (t != null) {
                for (int i = 0; i < t.numOutputs(); i++) {
                    Transaction.Output output = t.getOutput(i);
                    UTXO utxo = new UTXO(t.getHash(),i);
                    genutxoPool.addUTXO(utxo,output);
                }
                txPool.addTransaction(t);
            }
        }
        BlockNode genesisBlockNode = new BlockNode(genesisBlock, 1, genutxoPool, txPool);
        maxHeightBlockNode = genesisBlockNode;
        blockChainList.add(genesisBlockNode);
    }

    public BlockNode getParentNode(byte[] blockHash) {
        ByteArrayWrapper b1 = new ByteArrayWrapper(blockHash);
        for (BlockNode blockNode : blockChainList) {
            ByteArrayWrapper b2 = new ByteArrayWrapper(blockNode.block.getHash());
            if (b1.equals(b2)) {
                return blockNode;
            }
        }
        return null;
    }

    public void updateMaxHeightNode() {
        BlockNode currentMaxHeightNode = maxHeightBlockNode;
        for (BlockNode blockNode : blockChainList) {
            if (blockNode.height > currentMaxHeightNode.height) {
                currentMaxHeightNode = blockNode;
            } else if (blockNode.height == currentMaxHeightNode.height) {
                if (currentMaxHeightNode.timeCreated.after(blockNode.timeCreated)) {
                    currentMaxHeightNode = blockNode;
                }
            }
        }
        maxHeightBlockNode = currentMaxHeightNode;
    }

//    /**
//     * Replace current blockChainList with list only containing blocks whose (age > (maxHeight - Cut_OFF_AGE))
//     */
//    public void trimChain(){
//        ArrayList<BlockNode> trimmedChain = new ArrayList<>();
//        for (BlockNode node : blockChainList){
//            if (node.height >= maxHeightBlockNode.height - CUT_OFF_AGE + 1){
//                trimmedChain.add(node);
//            }
//        }
//        blockChainList = trimmedChain;
//    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightBlockNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightBlockNode.utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <= CUT_OFF_AGE + 1}.
     * As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {

//        if (block == null){
//            return false;
//        }
//        if (block.getHash() == null){
//            return false;
//        }
//        if (block.getCoinbase() == null){
//            return false;
//        }
//        if (block.getTransactions() == null){
//            return false;
//        }
        //Return false if block is a genesis block
        if (block.getPrevBlockHash() == null) {
            return false;
        }
        //Make sure block's parent is in the chain
        BlockNode parentNode = getParentNode(block.getPrevBlockHash());
        if(parentNode == null) {
            return false;
        }
        //Make sure new block height is >= (maxHeight - CUT_OFF_AGE)
        int blockHeight = parentNode.height+1;
        if (blockHeight <= (maxHeightBlockNode.height - CUT_OFF_AGE)) {
            return false;
        }
        //Make sure block's txs are valid
        UTXOPool utxoPool = new UTXOPool(parentNode.getUTXOPool());
        TransactionPool tPool = new TransactionPool(parentNode.getTransactionPool());
        for (Transaction tx : block.getTransactions()) {
            TxHandler txHandler = new TxHandler(utxoPool);
            if (!txHandler.isValidTx(tx)) {
                return false;
            }
            //remove used utxo
            for (Transaction.Input input : tx.getInputs()) {
                int outputIndex = input.outputIndex;
                byte[] prevTxHash = input.prevTxHash;
                UTXO utxo = new UTXO(prevTxHash, outputIndex);
                utxoPool.removeUTXO(utxo);
            }
            //add new utxo
            byte[] hash = tx.getHash();
            for (int i=0;i<tx.numOutputs();i++) {
                UTXO utxo = new UTXO(hash, i);
                utxoPool.addUTXO(utxo, tx.getOutput(i));
            }
        }
        //update utxo transaction coinbase
        for (int i = 0; i < block.getCoinbase().numOutputs(); i++) {
            utxoPool.addUTXO(new UTXO(block.getCoinbase().getHash(),i),block.getCoinbase().getOutput(i));
        }
        //Remove txs from tPool
        for (Transaction tx : block.getTransactions()) {
            tPool.removeTransaction(tx.getHash());
        }
        //Add the new block to blockChainList
        BlockNode newBlock = new BlockNode(block,blockHeight,utxoPool,tPool);
        boolean addNewBlock = blockChainList.add(newBlock);
        if (addNewBlock) {
            updateMaxHeightNode();
//            trimChain();
        }
        return addNewBlock;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }
}