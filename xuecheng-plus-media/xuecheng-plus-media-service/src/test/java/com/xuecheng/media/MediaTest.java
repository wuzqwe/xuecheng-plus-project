package com.xuecheng.media;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import io.minio.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 测试minio的sdk
 */
public class MediaTest {

    static MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://192.168.130.102:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();

    //上传文件
    @Test
    void test_upload() {
        try {
            //根据扩展名取出mimeType
            ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(".mp4");
            String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;//通用mimeType，字节流
            if (extensionMatch != null) {
                mimeType = extensionMatch.getMimeType();
            }
            UploadObjectArgs testbucket = UploadObjectArgs.builder()
                    .bucket("testbucket")
//                    .object("test001.mp4")
                    .object("001/test001.mp4")//添加子目录
                    .filename("C:\\Users\\吾空白\\Videos\\Captures\\1.mp4")
                    .contentType(mimeType)//默认根据扩展名确定文件内容类型，也可以指定
                    .build();
            minioClient.uploadObject(testbucket);
            System.out.println("上传成功");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("上传失败");
        }
    }

    //删除文件
    @Test
    public void delete() {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket("testbucket").object("001/test001.mp4").build());
            System.out.println("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("删除失败");
        }
    }

    //查询文件
    @Test
    public void getFile() {
        GetObjectArgs getObjectArgs = GetObjectArgs.builder().bucket("testbucket").object("001/test001.mp4").build();
        try (
                FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
                FileOutputStream outputStream = new FileOutputStream(new File("C:\\Users\\吾空白\\Videos\\Captures\\1a.mp4"));
        ) {
            IOUtils.copy(inputStream, outputStream);
            //校验文件的完整性对文件的内容进行md5
            FileInputStream fileInputStream1 = new FileInputStream(new File("C:\\Users\\吾空白\\Videos\\Captures\\1.mp4"));
            String source_md5 = DigestUtils.md5Hex(fileInputStream1); //minio中文件的md5
            FileInputStream fileInputStream = new FileInputStream(new File("C:\\Users\\吾空白\\Videos\\Captures\\1a.mp4"));
            String local_md5 = DigestUtils.md5Hex(fileInputStream);
            if (source_md5.equals(local_md5)) {
                System.out.println("下载成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //将分块文件上传到minio
    @Test
    public void uploadChunk() {
        String chunkFolderPath = "C:\\Users\\吾空白\\Videos\\Captures\\chunk";
        File chunkFolder = new File(chunkFolderPath);
        //分块文件
        File[] files = chunkFolder.listFiles();
        //将分块文件上传至minio
        for (int i = 0; i < files.length; i++) {
            try {
                UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder().
                        bucket("testbucket").object("chunk/" + i).filename(files[i].getAbsolutePath()).build();
                minioClient.uploadObject(uploadObjectArgs);
                System.out.println("上传分块成功" + i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //调用minio接口合并分块

    @Test
    void testMerge() throws Exception {

        List<ComposeSource> sources = Stream.iterate(0, i -> ++i)
                .limit(16)
                .map(i -> ComposeSource.builder()
                        .bucket("testbucket")
                        .object("chunk/".concat(Integer.toString(i)))
                        .build())
                .collect(Collectors.toList());

        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder().bucket("testbucket").object("merge01.mp4").sources(sources).build();
        minioClient.composeObject(composeObjectArgs);
    }


    //批量清理分块


}
