package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VideoTask {

    @Autowired
    private MediaFileProcessService mediaFileProcessService;

    @Value("${videoprocess.ffmpegpath}")
    private String ffmpegpath;

    @Autowired
    private MediaFileService mediaFileService;

    /**
     * 视频处理任务
     */
    @XxlJob("videoJobHandle")
    public void videoJobHandler() throws Exception{
        //分片参数
        int shardIndex= XxlJobHelper.getShardIndex();//执行器的序号，从0开始
        int shardTotal= XxlJobHelper.getShardTotal();//执行器总数

        //确定cpu的核数
        int processors = Runtime.getRuntime().availableProcessors();

        //查询待处理的任务
        List<MediaProcess> mediaProcessList = mediaFileProcessService.getMediaProcessList(shardIndex, shardTotal, processors);

        //任务数量
        int size = mediaProcessList.size();
        if (size<=0){
            return;
        }

        //创建一个线程池
        ExecutorService executorService = Executors.newFixedThreadPool(size);
        //使用计数器
        CountDownLatch countDownLatch = new CountDownLatch(size);
        mediaProcessList.forEach(mediaProcess -> {
            //将任务加入线程池
            executorService.execute(()->{
                //任务执行逻辑
                //开启任务
                try {
                    Long taskId = mediaProcess.getId();
                    boolean b = mediaFileProcessService.startTask(taskId);
                    if (!b){
                        log.debug("抢占任务失败，任务id:{}",taskId);
                        return;
                    }
                    //下载minio视频到本地
                    //桶
                    String bucket = mediaProcess.getBucket();
                    String objectName = mediaProcess.getFilePath();

                    //文件的id就是md5
                    String fileId = mediaProcess.getFileId();

                    File file = mediaFileService.downloadFileFromMinIO(bucket, objectName);
                    if (file==null){
                        log.debug("下载视频出错，任务id:{},bucket:{},objectName:{}",taskId,bucket,objectName);
                        //保存任务处理失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(taskId,"3",fileId,null
                                ,"下载视频到本地失败");
                        return;
                    }

                    //执行视频转码
                    //ffmpeg的路径
                    String ffmpeg_path = ffmpegpath;//ffmpeg的安装位置
                    //源avi视频的路径
                    String video_path = file.getAbsolutePath();
                    //转换后mp4文件的名称
                    String mp4_name = fileId+".mp4";
                    //转换后mp4文件的路径
                    //先创建一个临时文件，作为转换后的文件
                    File mp4File=null;
                    try {
                        mp4File=File.createTempFile("minio",".mp4");
                    } catch (IOException e) {
                        log.debug("创建临时文件异常,{}",e.getMessage());
                        return;
                    }
                    String mp4_path = mp4File.getAbsolutePath();
                    //创建工具类对象
                    Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpeg_path,video_path,mp4_name,mp4_path);
                    //开始视频转换，成功将返回success
                    String s = videoUtil.generateMp4();
                    if (!s.equals("success")){
                        log.debug("视频转码失败，原因:{},bucket:{},objectName:{}",s,bucket,objectName);
                        mediaFileProcessService.saveProcessFinishStatus(taskId,"3",fileId,null
                                ,s);
                        return;
                    }
                    //上传到minio
                    boolean b1 = mediaFileService.addMediaFileToMinIO(mp4File.getAbsolutePath(), "video.mp4", bucket, objectName);
                    if (!b1){
                        log.debug("上传mp4到minio失败，taskId:{}",taskId);
                        mediaFileProcessService.saveProcessFinishStatus(taskId,"3",fileId,null
                                ,"上传mp4到minio失败");
                        return;
                    }

                    //mp4的url
                    String url = getFilePath(fileId, ".mp4");

                    //保存任务处理结果
                    mediaFileProcessService.saveProcessFinishStatus(taskId,"2",fileId,url,"保存任务成功");
                }finally {
                    //计数器-1
                    countDownLatch.countDown();
                }

            });


        });

        //阻塞,指定最大限制的阻塞时间,阻塞最多等待一定时间后就解除阻塞
        countDownLatch.await(30, TimeUnit.MINUTES);

    }
    private String getFilePath(String fileMd5,String fileExt){
        return   fileMd5.substring(0,1) + "/" + fileMd5.substring(1,2) + "/" + fileMd5 + "/" +fileMd5 +fileExt;
    }
}