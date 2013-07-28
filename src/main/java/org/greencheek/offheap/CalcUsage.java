package org.greencheek.offheap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads /proc/<pid>/maps
 * Expanded upon the perl script linked to from:
 * http://elinux.org/Runtime_Memory_Measurement#.2Fproc.2F.3Cpid.3E.2Fmaps
 * to show the memory associated with the files.
 *
 */
public class CalcUsage {

    public static class FileDataSizeComparable implements Comparator<FileData> {

        @Override
        public int compare(FileData o1, FileData o2) {
            return (o1.getMappedSize()>o2.getMappedSize() ? -1 : (o1.getMappedSize()==o2.getMappedSize() ? 0 : 1));
        }
    }

    public static class FileData {
        public static final ConcurrentHashMap<String,FileData> MAPPED_FILES = new ConcurrentHashMap<String, FileData>(1024);
        private final long mappedSize;
        private final String name;

        public FileData(String name,long size) {
            this.name=name;
            this.mappedSize=size;
        }

        public long getMappedSize() {
            return mappedSize;
        }

        public String toString() {
            StringBuilder b = new StringBuilder(100);
            b.append("                ");
            b.append(mappedSize).append('\t').append('\t').append('\t');
            b.append(name);
            return b.toString();
        }
    }

    public static class Data {
        private static final Pattern LINE = Pattern.compile("^(\\w+)-(\\w+) (....) (\\w+) (\\S+) (\\d+) *(.*)$");

        private final long memStack;
        private final long memData;
        private final long memReadOnly;
        private final long memPrivate;
        private final long memUnknown;
        private final long fileExecutable;
        private final long fileWrittable;
        private final long fileReadOnlyData;
        private final long fileData;
        private final long fileUnknown;
        private final long filePrivate;

        public Data() {
            this(0,0,0,0,0,0,0,0,0,0,0);
        }

        public Data(long fileData, long fileWritableCode, long fileReadOnlyData, long filePrivate, long fileUnknown, long fileExe,
                    long memData, long memStack, long memReadOnlyData, long memPrivate, long memUnknown) {
            this.fileData = fileData;
            this.fileWrittable = fileWritableCode;
            this.fileReadOnlyData = fileReadOnlyData;
            this.filePrivate = filePrivate;
            this.fileUnknown = fileUnknown;
            this.fileExecutable = fileExe;
            this.memStack = memStack;
            this.memData = memData;
            this.memReadOnly = memReadOnlyData;
            this.memPrivate = memPrivate;
            this.memUnknown = memUnknown;
        }

        // probably needs to deal with shared
        public Data parseLine(String line) {
            Matcher matcher = LINE.matcher(line);
            if(matcher.matches()) {

                String hexLong1 = matcher.group(1);
                String hexLong2 = matcher.group(2);
                String permString = matcher.group(3);

                long size = new BigInteger(hexLong2,16).subtract(new BigInteger(hexLong1, 16)).longValue();

                String device = matcher.group(5);
                if(device.equals("00:00")) {
                    // memory
                    if(permString.startsWith("rwx")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack+size,memReadOnly,memPrivate,memUnknown);
                    }
                    else if(permString.startsWith("rw-")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData+size,memStack,memReadOnly,memPrivate,memUnknown);
                    }
                    else if(permString.startsWith("r--")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly+size,memPrivate,memUnknown);
                    }
                    else if(permString.startsWith("---")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate+size,memUnknown);
                    }
                    else {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate,memUnknown+size);
                    }
                }
                else {
                    //file
                    String filename = matcher.group(7);

                    FileData fileBasedData = FileData.MAPPED_FILES.get(filename);
                    if(fileBasedData==null) {
                        FileData.MAPPED_FILES.put(filename,new FileData(filename,size/1024));
                    } else {
                        FileData.MAPPED_FILES.put(filename,new FileData(filename,(fileBasedData.getMappedSize()+size)/1024));
                    }

                    if(permString.startsWith("r-x")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable+size,
                                memData,memStack,memReadOnly,memPrivate,memUnknown);

                    }
                    else if(permString.startsWith("rwx")) {
                        return new Data(fileData,fileWrittable+size,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate,memUnknown);
                    }
                    else if(permString.startsWith("rw-")) {
                        return new Data(fileData+size,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate,memUnknown);
                    }
                    else if(permString.startsWith("r--")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData+size,filePrivate,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate,memUnknown);
                    }
                    else if(permString.startsWith("---")) {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate+size,fileUnknown,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate,memUnknown);
                    }
                    else {
                        return new Data(fileData,fileWrittable,fileReadOnlyData,filePrivate,fileUnknown+size,fileExecutable,
                                memData,memStack,memReadOnly,memPrivate,memUnknown);
                    }
                }

            } else {
                return this;
            }
        }


        public String toString() {
            StringBuilder s = new StringBuilder(1000);
            s.append("Backed by file:").append("\n");
            s.append("                Executable                r-x  ");
            s.append(this.fileExecutable/1024).append("\n");
            s.append("                Write/Exec (jump tables)  rwx  ");
            s.append(this.fileWrittable/1024).append("\n");
            s.append("                Read Only data            r--  ");
            s.append(this.fileReadOnlyData/1024).append("\n");
            s.append("                Data                      rw-  ");
            s.append(this.fileData/1024).append("\n");
            s.append("                Private                   ---  ");
            s.append(this.filePrivate/1024).append("\n");
            s.append("                Unknown                        ");
            s.append(this.fileUnknown/1024).append("\n");
            s.append("Anonymous:").append("\n");
            s.append("                Writable code (stack)     rwx  ");
            s.append(this.memStack/1024).append("\n");
            s.append("                Data (malloc, mmap)       rw-  ");
            s.append(this.memData/1024).append("\n");
            s.append("                RO data                   r--  ");
            s.append(this.memReadOnly/1024).append("\n");
            s.append("                Private                   ---  ");
            s.append(this.memPrivate/1024).append("\n");
            s.append("                Unknown                        ");
            s.append(this.memUnknown/1024).append("\n");
            return s.toString();
        }

    }


    public static void main(String[] args) {

        String pid = "self";
        if(args.length>0) {
            pid = args[0];
        }

        BufferedReader br = null;
        List<String> lines = new ArrayList<String>(120);
        try {

            String sCurrentLine;

            br = new BufferedReader(new FileReader("/proc/"+pid+"/maps"));

            while ((sCurrentLine = br.readLine()) != null) {
                lines.add(sCurrentLine);
            }

        } catch (IOException e) {
            System.err.println("No such file:" + "/proc/" + pid + "/maps");
            System.exit(-1);
        } finally {
            try {
                if (br != null)br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }


        Data d = new Data();
        for(String line : lines) {
            d = d.parseLine(line);
        }

        System.out.println(d);
        System.out.println("Mapped File Info:");
        List<FileData> files = new ArrayList<FileData>(FileData.MAPPED_FILES.size());
        for(FileData file : FileData.MAPPED_FILES.values()) {
            files.add(file);
        }

        Collections.sort(files,new FileDataSizeComparable());
        for(FileData f : files) {
            System.out.println(f);
        }
    }
}
