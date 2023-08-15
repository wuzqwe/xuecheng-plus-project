package com.xuecheng.media;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 测试大文件上传方法
 */
public class BigFileTest {

    //分块测试
    @Test
    void testChunk() throws Exception{
        //将源文件
        File sourcefile = new File("C:\\Users\\吾空白\\Videos\\Captures\\1.mp4");
        //分块文件的存储路径
        String chunkFilePath="C:/Users/吾空白/Videos/Captures/chunk/";
        //分块文件的大小
        int chunkSizq=1024*1024*11;
        //分块文件个数
        int chunNum=(int )Math.ceil(sourcefile.length()*1.0/chunkSizq);
        //使用流从源文件读数据，向分块文件中写数据
        RandomAccessFile raf_r = new RandomAccessFile(sourcefile, "r");

        //缓存区
        byte[] bytes=new byte[1024];

        for (int i=0;i<chunNum;i++){
            File chunkFile = new File(chunkFilePath + i);
            //分块文件写入流
            RandomAccessFile raf_rw = new RandomAccessFile(chunkFile, "rw");
            int len=-1;
            while ((len=raf_r.read(bytes))!=-1){
                raf_rw.write(bytes,0,len);
                if (chunkFile.length()>=chunkSizq) {
                    break;
                }
            }
            raf_rw.close();
        }
        raf_r.close();
    }

    //将分块进行合并
    @Test
    public void testMerge() throws IOException {
        //块文件目录
        File chunkFolder = new File("C:\\Users\\吾空白\\Videos\\Captures\\chunk");
        //源文件
        File sourceFile = new File("C:\\Users\\吾空白\\Videos\\Captures\\1.mp4");
        //合并后的文件
        File mergeFile = new File("C:\\Users\\吾空白\\Videos\\Captures\\3.mp4");

        //取出所有分块文件
        File[] files = chunkFolder.listFiles();
        //将数组转为list
        List<File> fileList = Arrays.asList(files);

        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Integer.parseInt(o1.getName())-Integer.parseInt(o2.getName());
            }
        });
        //向合并文件写的流
        RandomAccessFile raf_rw = new RandomAccessFile(mergeFile, "rw");

        //缓冲区
        byte[] bytes=new byte[1024];
        //遍历分块文件，向合并的文件写
        for (File file : fileList){
            //读分块的流
            RandomAccessFile raf_r = new RandomAccessFile(file, "r");
            int len=-1;
            while ((len= raf_r.read(bytes))!=-1){
                raf_rw.write(bytes,0,len);
            }
            raf_r.close();
        }
        raf_rw.close();

        //校验文件
        try (

                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                FileInputStream mergeFileStream = new FileInputStream(mergeFile);

        ) {
            //取出原始文件的md5
            String originalMd5 = DigestUtils.md5Hex(fileInputStream);
            //取出合并文件的md5进行比较
            String mergeFileMd5 = DigestUtils.md5Hex(mergeFileStream);
            if (originalMd5.equals(mergeFileMd5)) {
                System.out.println("合并文件成功");
            } else {
                System.out.println("合并文件失败");
            }
        }
    }
}
