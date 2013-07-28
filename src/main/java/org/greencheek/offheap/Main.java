package org.greencheek.offheap;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: dominictootell
 * Date: 27/07/2013
 * Time: 15:21
 * To change this template use File | Settings | File Templates.
 */
public class Main {



    public static void main(String[] args) {



        ByteBuffer buffers[];

        int numberOfMb=1024;
        try {
            if(args.length>0) {
                numberOfMb = Integer.parseInt(args[0]);
            }
        } catch (NumberFormatException e) {
            numberOfMb = 1024; //1gb
        }



        System.out.println("number of mb:" + numberOfMb);
        System.out.flush();
        buffers = new ByteBuffer[numberOfMb];


        int sleepTimePerMb = 100;  // 100ms
        try {
            if(args.length>1) {
                sleepTimePerMb = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
           sleepTimePerMb = 100;
        }

        String mmapFile = "/tmp/test.out";
        if(args.length>2) {
            mmapFile = args[2];
        }


        FileChannel mmapedFile = null;
        MappedByteBuffer out = null;

        try {
            System.out.println("Mapping file:" + mmapFile);
            System.out.flush();
            mmapedFile = new RandomAccessFile(mmapFile, "rw").getChannel();

            out = mmapedFile
                        .map(FileChannel.MapMode.READ_WRITE, 0, (1024*1024)*2);

            out.load();

        } catch(IOException e) {
            e.printStackTrace();
            if(mmapedFile!=null) {
                try {
                    mmapedFile.close();
                }catch (IOException e2) {

                }
            }
        }

        try {
            final int sleepTimeMbPerMb = sleepTimePerMb;
            for(int i = 0 ; i<numberOfMb;i++) {

                buffers[i] = ByteBuffer.allocateDirect(1024*1024);

                try {
                    Thread.sleep(sleepTimeMbPerMb);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }

            System.out.println("allocated: " + numberOfMb + " objects");
            System.out.println("Sleeping for 1 min");
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        } finally {
            if(mmapedFile!=null) {
                try {
                    mmapedFile.close();
                }catch (IOException e2) {

                }
            }
        }


    }
}
