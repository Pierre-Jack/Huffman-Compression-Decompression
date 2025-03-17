import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

    public class Compressor{
        private static final int BUFFER_SIZE_LIMIT = 1<<17; // 128KB
        private int BUFFER_SIZE;

        String inputFile = "input.txt";
        String outputFile = "output.bin";
        String compressedFile = "output.bin";
        String decompressedFile = "decompressed.txt";

        private class ByteArrayWrapper {
            private byte[] data;

            ByteArrayWrapper(byte[] data) {
                this.data = data;
            }

            @Override
            public boolean equals(Object obj) {
                ByteArrayWrapper that = (ByteArrayWrapper) obj;
                return Arrays.equals(data, that.data);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(data);
            }

            public byte[] getData() {
                return data;
            }
            public void setData(byte[] data){
                this.data = data;
            }
        }
        private int n;
        Node root;
        FreqMap freq;
        public BytesToCodeMap bytesToCode;
        public CodeToBytesMap codeToBytes;
        long fileSize;

        public void printMap(){
            bytesToCode.printMap();
        }

        public Compressor(int n){
            this.n = n;
            bytesToCode = new HashBytesToCodeMap();
            codeToBytes = new HashCodeToBytesMap();
            freq = new HashFreqMap();
            BUFFER_SIZE = BUFFER_SIZE_LIMIT/n*n;
        }
        public Compressor(int n, String inputFile, String decompressedFile, String outputFile){
            this.n = n;
            bytesToCode = new HashBytesToCodeMap();
            codeToBytes = new HashCodeToBytesMap();
            freq = new HashFreqMap();
            BUFFER_SIZE = BUFFER_SIZE_LIMIT/n*n;
            this.inputFile = inputFile;
            this.decompressedFile = decompressedFile;
            this.outputFile = outputFile;
        }
        class Node implements Comparable<Node>{
            int val;
            Node left;
            Node right;
            Node parent;
            byte[] bytes;
            Node(int val){
                this.val = val;
            }
            Node(int val, byte[] bytes){
                this.val = val;
                this.bytes = bytes;
            }
            Node(Node left, Node right, int val){
                this.left = left;
                this.right = right;
                this.val = val;
            }
            Node(Node parent){
                this.parent = parent;
            }
            Node(Node left, Node right, Node parent){
                this.left = left;
                this.right = right;
                this.parent = parent;
            }

            @Override
            public int compareTo(Node o) {
                return this.val-o.val;
            }

            public int printTree(int n){
                System.out.print("node " + n + ": " + val);
                if(left != null){
                    System.out.print(", children of " + n + ": " + left.val + " " + right.val);
                    System.out.println();
                    int m = left.printTree(n+1);
                    right.printTree(m+1);
                }
                else{
                    System.out.print(", leaf node: " + Arrays.toString(bytes) + " (" + Arrays.hashCode(bytes)+")");
                    System.out.println();
                }
                return n;


            }
        }

        // Abstract Maps
        private abstract class FreqMap {
            public abstract boolean containsKey(byte[] bytes);
            public abstract void put(byte[] bytes, int val);
            public abstract void inc(byte[] bytes);
            public abstract Set<byte[]> keySet();
            public abstract long get(byte[] bytes);

        }
        private abstract class CodeToBytesMap {
            public abstract boolean containsKey(long key);
            public abstract void put(long key, byte[] val);
            public abstract byte[] get(long key);
            public abstract Set<Long> keySet();

        }
        private abstract class BytesToCodeMap {
            public abstract boolean containsKey(byte[] bytes);
            public abstract Set<byte[]> keySet();
            public abstract void put(byte[] bytes, long val);
            public abstract long get(byte[] bytes);
            public abstract void printMap();
        }

        // Concrete Maps
        private class ArrayCodeToBytesMap extends CodeToBytesMap {
            // Assumes code is <= 26 bits, n <= 8 , should be switched to another implementation otherwise
            private int CODE_SIZE = 20;
            private long[] map;
            private boolean[] contains;

            ArrayCodeToBytesMap(){
                map = new long[1<<CODE_SIZE]; // <64MB
                contains = new boolean[1<<CODE_SIZE];
            }
            ArrayCodeToBytesMap(int codeSize){
                this.CODE_SIZE = codeSize;
                map = new long[1<<CODE_SIZE]; // <64MB
                contains = new boolean[1<<CODE_SIZE];
            }
            public boolean containsKey(long key){
                return contains[(int)(key >> (62-CODE_SIZE))]; // 6-bits remain for the index, CODE_SIZE-6 bits for the map
            }
            public void put(long key, byte[] val){
                long bytes = 0;
                for(byte b : val){
                    bytes <<= 8;
                    bytes |= b & 0xFF;
                }
                map[(int)(key >> (62-CODE_SIZE))] = bytes;
                contains[(int)(key >> (62-CODE_SIZE))] = true;
            }
            public byte[] get(long key){
                long val = map[(int)(key >> (62-CODE_SIZE))];
                byte[] bytes = new byte[n];
                for(int i=0; i<n; i++){
                    bytes[i] = (byte)(val >> (8*(n-i-1)));
                }
                return bytes;

            }
            public Set<Long> keySet(){
                Set<Long> set = new HashSet<>();
                for(int i=0; i<map.length; i++){
                    if(contains[i]){
                        set.add((long)i);
                    }
                }
                return set;
            }
        }
        private class HashCodeToBytesMap extends CodeToBytesMap {
            private Map<Long, byte[]> map;
            HashCodeToBytesMap(){
                map = new HashMap<>();
            }
            public boolean containsKey(long key){
                return map.containsKey(key);
            }
            public void put(long key, byte[] val){
                map.put(key, val);
            }
            public byte[] get(long key){
                return map.get(key);
            }
            public Set<Long> keySet(){
                return map.keySet();
            }

        }
        private class HashBytesToCodeMap extends BytesToCodeMap {
            private ByteArrayWrapper wrapper;
            private Set<byte[]> keySet;
            private Map<ByteArrayWrapper, Long> map;
            HashBytesToCodeMap(){
                map = new HashMap<>();
                keySet = new HashSet<>();
                wrapper = new ByteArrayWrapper(new byte[n]);
            }
            public Set<byte[]> keySet(){
                return keySet;
            }
            public boolean containsKey(byte[] bytes){
                wrapper.setData(bytes);
                return map.containsKey(wrapper);
            }
            public void put(byte[] bytes, long val){
                ByteArrayWrapper newWrapper = new ByteArrayWrapper(bytes);
                map.put(newWrapper, val);
                keySet.add(bytes);
            }
            public long get(byte[] bytes){
                wrapper.setData(bytes);
                return map.get(wrapper);
            }
            public void printMap() {
                // format hex
                for (byte[] key : keySet()) {
                    System.out.println("(" + key + ") "
                            + Long.toHexString(map.get(key))
                            + " " + map.get(key));
                }
            }
        }
        private class ArrayBytesToCodeMap extends BytesToCodeMap {
            private long[] map;
            private boolean[] contains;

            ArrayBytesToCodeMap(){
                map = new long[1<<8*n];
                contains = new boolean[1<<8*n];
            }
            public boolean containsKey(byte[] bytes){
                int i = 0;
                for(byte b : bytes){
                    i |= b & 0xFF;
                    i <<= 8;
                }
                i >>= 8;
                return contains[i];
            }
            public void put(byte[] bytes, long val){
                int i = 0;
                for(byte b : bytes){
                    i |= b & 0xFF;
                    i <<= 8;
                }
                i >>= 8;
                map[i] = val;
                contains[i] = true;
            }
            public long get(byte[] bytes){
                int i = 0;
                for(byte b : bytes){
                    i |= b & 0xFF;
                    i <<= 8;
                }
                i >>= 8;
                return map[i];
            }
            public Set<byte[]> keySet(){
                Set<byte[]> set = new HashSet<>();
                for(int i=0; i<map.length; i++){
                    if(map[i] != 0){
                        byte[] bytes = new byte[n];
                        for(int j=0; j<n; j++){
                            bytes[j] = (byte)(i>>(8*(n-j-1)));
                        }
                        set.add(bytes);
                    }
                }
                return set;
            }
            public void printMap() {
                // format hex
                for (int i = 0; i < map.length; i++) {
                    if (contains[i]) {
                        byte[] bytes = new byte[n];
                        for (int j = 0; j < n; j++) {
                            bytes[j] = (byte) (i >> (8 * (n - j - 1)));
                        }
                        System.out.println("(" + Arrays.hashCode(bytes) + ") "
                                + Long.toHexString(map[i])
                                + " " + map[i]);
                    }
                }
            }
        }
        private class ArrayFreqMap extends FreqMap {
            private long[] map;
            ArrayFreqMap(){
                map = new long[1<<(8*n)];
            }
            public boolean containsKey(byte[] bytes){
                return true;
            }
            public void put(byte[] bytes, int val){
                int i = 0;
                for(byte b : bytes){
                    i |= b & 0xFF;
                    i <<= 8;
                }
                i >>= 8;
                map[i] = val;
            }
            public void inc(byte[] bytes){
                int i = 0;
                for(byte b : bytes){
                    i |= b & 0xFF;
                    i <<= 8;
                }
                i >>= 8;
                map[i]++;
            }
            public Set<byte[]> keySet(){
                Set<byte[]> set = new HashSet<>();
                for(int i=0; i<map.length; i++){
                    if(map[i] != 0){
                        byte[] bytes = new byte[n];
                        for(int j=0; j<n; j++){
                            bytes[j] = (byte)(i>>(8*(n-j-1)));
                        }
                        set.add(bytes);
                    }
                }
                return set;
            }
            public long get(byte[] bytes){
                int i = 0;
                for(byte b : bytes){
                    i |= b & 0xFF;
                    i <<= 8;
                }
                i >>= 8;
                return map[i];
            }

        }
        private class HashFreqMap extends FreqMap {
            private ByteArrayWrapper wrapper;
            private Map<ByteArrayWrapper, Integer> map;
            Set<byte[]> set = new HashSet<>();
            HashFreqMap(){
                map = new HashMap<>();
                wrapper = new ByteArrayWrapper(new byte[n]);
            }
            public boolean containsKey(byte[] bytes){
                wrapper.setData(bytes);
                return map.containsKey(wrapper);
            }
            public void put(byte[] bytes, int val){
                ByteArrayWrapper wrapper = new ByteArrayWrapper(bytes);
                map.put(wrapper, val);
                set.add(bytes);
            }
            public void inc(byte[] bytes){
                ByteArrayWrapper wrapper = new ByteArrayWrapper(bytes);
                if(map.containsKey(wrapper)){
                    map.put(wrapper, map.get(wrapper)+1);
                }
                else{
                    map.put(wrapper, 1);
                    set.add(bytes);
                }
            }
            public Set<byte[]> keySet(){
                return set;
            }
            public long get(byte[] bytes){
                wrapper.setData(bytes);
                return map.get(wrapper);
            }
        }
        public void captureFileFreq() throws Exception {
            try (FileInputStream fis = new FileInputStream(inputFile);
                 FileChannel inputChannel = fis.getChannel();) {
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

                while (inputChannel.read(buffer) > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    captureFreq(bytes);
                    buffer.clear();
                }

            }
        }
        public void captureFreq(byte[] file){
            int l = file.length;
            for(int i=0; i+n-1 < l; i += n){
                byte[] bytes = new byte[n];
                for(int j=0; j<n; j++){
                    bytes[j] = file[i+j];
                }
                freq.inc(bytes);
            }
            if(l%n != 0){
                int m=l%n;
                byte[] bytes = new byte[n];
                for(int i=0; i<n; i++){
                    if(l-m+i < l){
                        bytes[i] = file[l-m+i];
                    }
                    else{
                        bytes[i] = 0x00;
                    }
                }
                freq.inc(bytes);
            }
        }
        public Node constructHuffmanTree(){
            PriorityQueue<Node> Q = new PriorityQueue<>();
            for(byte[] bytes : freq.keySet()){
                Q.add(new Node((int)freq.get(bytes), bytes));
            }
            while(!(Q.size()==1)){
                Node x = Q.remove();
                Node y = Q.remove();
                Q.add(new Node(x, y, x.val+y.val));
            }
            root = Q.remove();
            return root;
        }
        public void constructCodeMap(Node node, long code, long codeSize){
            if(node.left == null){
                code <<= 56-codeSize;
                bytesToCode.put(node.bytes, code | (codeSize<<56));
            }
            else{
                constructCodeMap(node.left, code<<1, codeSize+1);
                constructCodeMap(node.right, (code<<1)+1, codeSize+1);
            }
            root = null;
        }
        public void constructBytesMap(){
            for(byte[] bytes : bytesToCode.keySet()){
                codeToBytes.put(bytesToCode.get(bytes), bytes);
            }
        }
        private class CodeWriter{
            byte bufferByte;
            int bufferIndex;
            int codeSize;
            int codeIndex;
            int totalSize;
            int padding;
            byte[] compressedTemp;
            CodeWriter(int size){
                compressedTemp = new byte[size];
            }
            public void write(long code){
                codeSize = (int) (code >> 56);
                codeIndex = 0;
                code &= 0x00FFFFFFFFFFFFFFL;
                while(codeIndex < codeSize){
                    if (8-bufferIndex <= codeSize-codeIndex){
                        bufferByte |= (((byte)(code >> (56 -codeIndex-8))) >>> (bufferIndex) & (0xFF >>> bufferIndex));
                        codeIndex += 8-bufferIndex;
                        compressedTemp[totalSize] = bufferByte;
                        totalSize++;
                        bufferByte = 0;
                        bufferIndex = 0;
                    }
                    else{
                        bufferByte |= (((byte)(code >> (56 -codeIndex-8))) >>> (bufferIndex) & (0xFF >>> bufferIndex));
                        bufferIndex += codeSize-codeIndex;
                        codeIndex = codeSize;
                    }
                }
            }
            public byte[] getCompressed(){
                byte[] compressed = new byte[totalSize];
                for(int i=0; i<totalSize; i++){
                    compressed[i] = compressedTemp[i];
                }
                return compressed;
            }
        }
        public void writeInt(byte[] arr, int val, int index){
            arr[index] = (byte)(val >> 24);
            arr[index+1] = (byte)(val >> 16);
            arr[index+2] = (byte)(val >> 8);
            arr[index+3] = (byte)(val);
        }
        public void writeLong(byte[] arr, long val, int index){
            arr[index] = (byte)(val >> 56);
            arr[index+1] = (byte)(val >> 48);
            arr[index+2] = (byte)(val >> 40);
            arr[index+3] = (byte)(val >> 32);
            arr[index+4] = (byte)(val >> 24);
            arr[index+5] = (byte)(val >> 16);
            arr[index+6] = (byte)(val >> 8);
            arr[index+7] = (byte)(val);

        }
        public byte[] writeMap(){
            int index = 4;
            byte[] map = new byte[4+4+8+(8+n)*codeToBytes.keySet().size()];
            writeInt(map, n, index); index+=4;
            writeLong(map, fileSize, index); index+=8;
            for(long key : codeToBytes.keySet()){
                writeLong(map, key, index); index+=8;
                byte[] bytes = codeToBytes.get(key);
                for(int i=0; i<n; i++){
                    map[index] = bytes[i]; index++;
                }
            }
            writeInt(map, index, 0);
            return map;
        }

        public void readMap(byte[] map){
            int index = 0;
            this.n = (map[index] << 24) | (map[index+1] << 16) | (map[index+2] << 8) | (map[index+3]);
            fileSize = ((long)map[index+4] << 56) | ((long)map[index+5] << 48) | ((long)map[index+6] << 40) | ((long)map[index+7] << 32) | ((long)map[index+8] << 24) | ((long)map[index+9] << 16) | ((long)map[index+10] << 8) | ((long)map[index+11]);
            index += 4+8;
            while(index+8+n <= map.length){
                long key = ((long)(map[index] & 0xFF) << 56) | ((long)(map[index+1] & 0xFF) << 48) | ((long)(map[index+2] & 0xFF) << 40) | ((long)(map[index+3] & 0xFF) << 32) | ((long)(map[index+4] & 0xFF) << 24) | ((long)(map[index+5] & 0xFF) << 16) | ((long)(map[index+6] & 0xFF) << 8) | ((long)(map[index+7] & 0xFF));
                index+=8;
                byte[] bytes = new byte[n];
                for(int i=0; i<n; i++){
                    bytes[i] = map[index];
                    index++;
                }
                codeToBytes.put(key, bytes);
            }
        }
        public byte[] compress(byte[] file, int compressedSize){
            CodeWriter writer = new CodeWriter(compressedSize);
            int l = file.length;
            for(int i=0; i+n-1 < l; i += n){
                byte[] bytes = new byte[n];
                for(int j=0; j<n; j++){
                    bytes[j] = file[i+j];
                }
                long code = bytesToCode.get(bytes);
                //System.out.println(bytes);
                writer.write(code);
            }
            if(l%n != 0){
                int m=l%n;
                byte[] bytes = new byte[n];
                for(int i=0; i<n; i++){
                    if(l-m+i < l){
                        bytes[i] = file[l-m+i];
                    }
                    else{
                        bytes[i] = 0x00;
                    }
                }
                long code = bytesToCode.get(bytes);
                writer.write(code);
            }
            if(writer.bufferIndex != 0){
                writer.compressedTemp[writer.totalSize] = writer.bufferByte;
                writer.totalSize++;
            }
            //System.out.println(writer.totalSize);
            return writer.getCompressed();
        }
        private class CodeReader{
            long bufferCode;
            byte codeSize=1;
            //int bufferIndex;
            int byteIndex;
            int totalSize;
            int padding;
            ArrayList<Byte> decompressed;
            CodeReader(){
                this.decompressed = new ArrayList<>();
            }
            public void read(byte b, int finalSize){
                byteIndex = 0;
                while(byteIndex < 8){
                    bufferCode &= 0x00FFFFFFFFFFFFFFL;
                    bufferCode |= ((long)codeSize) << 56;
                    if(b < 0){
                        bufferCode |= 1L << (56-codeSize);
                    }

                    if(codeToBytes.containsKey(bufferCode)){
                        byte[] bytes = codeToBytes.get(bufferCode);
                        for(int i=0; i<n; i++){
                            if(decompressed.size() < finalSize)
                                decompressed.add(bytes[i]);
                        }
                        bufferCode = 0;
                        codeSize = 0;
                    }
                    b <<= 1;
                    byteIndex++;
                    codeSize++;

                }
            }

            }
        public byte[] decompress(byte[] file, int decompressedSize){
            CodeReader reader = new CodeReader();
            int l = file.length;
            for(int i=0; i<l; i++){
                reader.read(file[i], decompressedSize);
            }
            byte[] result = new byte[reader.decompressed.size()];
            for(int i=0; i<reader.decompressed.size(); i++){
                result[i] = reader.decompressed.get(i);
            }
            return result;
        }

        public void compressFile() throws Exception {
            captureFileFreq();
            constructHuffmanTree();
            constructCodeMap(root, 0, 0);
            constructBytesMap();
            try (FileInputStream fis = new FileInputStream(inputFile);
                 FileChannel inputChannel = fis.getChannel();
                 FileOutputStream fos = new FileOutputStream(outputFile);
                 FileChannel outputChannel = fos.getChannel()) {

                ByteBuffer mapBuffer = ByteBuffer.wrap(writeMap());
                outputChannel.write(mapBuffer);

                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

                int bytesRead = inputChannel.read(buffer);
                while (bytesRead > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);


//                    if(bytesRead%n != 0){
//                        byte[] newBytes = new byte[bytesRead+n-bytesRead%n];
//                        for(int i=0; i<bytesRead; i++){
//                            newBytes[i] = bytes[i];
//                        }
//                        for(int i=bytesRead; i<(bytesRead+n-bytesRead%n); i++){
//                            newBytes[i] = newBytes[bytesRead-1];
//                        }
//                        bytes = newBytes;
//                    }
                    //System.out.println(Arrays.toString(bytes));
                    byte[] compressedBlock = compress(bytes, bytes.length * 8);
                    byte[] compressedBlockWithSize = new byte[compressedBlock.length+8];
                    for(int i=0; i<4; i++){
                        compressedBlockWithSize[i] = (byte)(compressedBlock.length >> (8*(3-i)));
                    }
                    for(int i=4; i<8; i++){
                        compressedBlockWithSize[i] = (byte)(bytes.length >> (8*(3-(i-4))));
                    }
                    for(int i=0; i<compressedBlock.length; i++){
                        compressedBlockWithSize[i+8] = compressedBlock[i];
                    }
                    //System.out.println(Arrays.toString(compressedBlockWithSize));
                    ByteBuffer compressedBuffer = ByteBuffer.wrap(compressedBlockWithSize);
                    //int blockSize = compressedBlock.length;
                    //ByteBuffer blockSizeBuffer = ByteBuffer.allocate(4);
                    //blockSizeBuffer.putInt(blockSize);
                    //blockSizeBuffer.flip();
                    //outputChannel.write(blockSizeBuffer);
                    outputChannel.write(compressedBuffer);
                    buffer.clear();
                    bytesRead = inputChannel.read(buffer);
                }
//                int extraBytes = (int)(outputChannel.size() % n);
//                // delete the last extraBytes from the file
//                outputChannel.truncate(outputChannel.size() - extraBytes);

            }
        }

        public void decompressFile() throws Exception {
            try (FileInputStream fis = new FileInputStream(compressedFile);
                 FileChannel inputChannel = fis.getChannel();
                 FileOutputStream fos = new FileOutputStream(decompressedFile);
                 FileChannel outputChannel = fos.getChannel()) {
                ByteBuffer mapSizeBuffer = ByteBuffer.allocate(4);
                inputChannel.read(mapSizeBuffer);
                mapSizeBuffer.flip();
                int mapSize = mapSizeBuffer.getInt();
                ByteBuffer mapBuffer = ByteBuffer.allocate(mapSize-4);
                inputChannel.read(mapBuffer);
                mapBuffer.flip();
                byte[] map = mapBuffer.array();
                //System.out.println(Arrays.toString(map));
                readMap(map);
                //System.out.println(Arrays.toString(codeToBytes.keySet().toArray()));

                ByteBuffer blockSizeBuffer = ByteBuffer.allocate(4);
                inputChannel.read(blockSizeBuffer);
                blockSizeBuffer.flip();
                int blockSize = blockSizeBuffer.getInt();
                ByteBuffer finalSizeBuffer = ByteBuffer.allocate(4);
                inputChannel.read(finalSizeBuffer);
                finalSizeBuffer.flip();
                int finalSize = finalSizeBuffer.getInt();
                ByteBuffer buffer = ByteBuffer.allocate(blockSize);


                int bytesRead = inputChannel.read(buffer);
                while (bytesRead > 0) {
                    buffer.flip();
                    //
                    byte[] bytes = new byte[bytesRead];
                    for(int i=0; i<bytesRead; i++){
                        bytes[i] = buffer.get();
                    }
                    ByteBuffer decompressedBuffer = ByteBuffer.wrap(decompress(bytes, finalSize));
                    //
                    outputChannel.write(decompressedBuffer);
                    //System.out.println(Arrays.toString(decompressedBuffer.array()));
                    buffer.clear();
                    blockSizeBuffer = ByteBuffer.allocate(4);
                    finalSizeBuffer = ByteBuffer.allocate(4);
                    if(inputChannel.read(blockSizeBuffer) < 0) break;
                    inputChannel.read(finalSizeBuffer);
                    blockSizeBuffer.flip();
                    finalSizeBuffer.flip();
                    blockSize = blockSizeBuffer.getInt();
                    finalSize = finalSizeBuffer.getInt();
                    buffer = ByteBuffer.allocate(blockSize);
                    bytesRead = inputChannel.read(buffer);
                }
            }
        }
        public static void main(String[] args) {
            if (args.length == 3) {
                String inputFile = args[1];
                int n = Integer.parseInt(args[2]);
                Compressor compressor = new Compressor(n, inputFile, "", "");
                File input = new File(inputFile);
                String outputFileName = String.format("%s.%d.%s.hc", "21010394", n, input.getName());
                File outputFile = new File(input.getParent(), outputFileName);
                compressor.outputFile = outputFile.getAbsolutePath();
                long startTime = System.currentTimeMillis();
                try{
                  compressor.compressFile();  
                }
                catch(Exception e){

                }
                
                long endTime = System.currentTimeMillis();
                long originalSize = new File(inputFile).length();
                long compressedSize = new File(compressor.outputFile).length();
                double compressionRatio = (double) compressedSize / originalSize;

                System.out.println("Compression completed.");
                System.out.println("Compression time: " + (endTime - startTime) + " ms");
                System.out.println("Compression ratio: " + compressionRatio);

            }
            else if(args.length == 2){
                String inputFile = args[1];
                Compressor compressor = new Compressor(1, "", inputFile, inputFile);
                compressor.compressedFile = inputFile;
                File input = new File(inputFile);
                String outputFileName = "extracted." + input.getName().replace(".hc", "");
                File outputFile = new File(input.getParent(), outputFileName);
                compressor.decompressedFile = outputFile.getAbsolutePath();
                long startTime = System.currentTimeMillis();
                try{
                  compressor.decompressFile();  
                }
                catch(Exception e){
                    e.printStackTrace();
                }

            long endTime = System.currentTimeMillis();

            System.out.println("Decompression completed.");
            System.out.println("Decompression time: " + (endTime - startTime) + " ms");
            }

        
        }
}
