package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.events.Event;

import javax.management.ObjectName;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2022/9/10 8:58
 */
@Service
@Slf4j
public class MediaFileServiceImpl implements MediaFileService {

    @Autowired
    MediaFilesMapper mediaFilesMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MediaFileService currentProxy;

    @Autowired
    private MediaProcessMapper mediaProcessMapper;

    @Value("${minio.bucket.files}")
    private String bucket_mediafiles;

    @Value("${minio.bucket.videofiles}")
    private String bucket_videofiles;


    @Override
    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto) {

        //构建查询条件对象
        LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MediaFiles::getCompanyId,companyId)
                .eq(queryMediaParamsDto.getAuditStatus()!=null,MediaFiles::getAuditStatus,queryMediaParamsDto.getAuditStatus())
                .eq(queryMediaParamsDto.getType()!=null,MediaFiles::getFileType,queryMediaParamsDto.getType())
                .like(queryMediaParamsDto.getFilename()!=null,MediaFiles::getFilename,queryMediaParamsDto.getFilename());

        //分页对象
        Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        // 查询数据内容获得结果
        Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);
        // 获取数据列表
        List<MediaFiles> list = pageResult.getRecords();
        // 获取数据总数
        long total = pageResult.getTotal();
        // 构建结果集
        PageResult<MediaFiles> mediaListResult = new PageResult<>(list, total, pageParams.getPageNo(), pageParams.getPageSize());
        return mediaListResult;

    }

    //根据扩展名获取获取mimeType
    private String getMimeType(String extension) {
        if (extension == null) {
            extension = "";
        }
        //根据扩展名取出mimeType
        ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
        String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;//通用mimeType，字节流
        if (extensionMatch != null) {
            mimeType = extensionMatch.getMimeType();
        }
        return mimeType;
    }

    /**
     * 将文件上传到minio
     *
     * @param localFilePath 文件本地路径
     * @param mimeType      媒体类型
     * @param bucket        桶
     * @param objectName    对象名
     * @return
     */
    public boolean addMediaFileToMinIO(String localFilePath, String mimeType, String bucket, String objectName) {
        try {
            UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)//添加子目录
                    .filename(localFilePath)
                    .contentType(mimeType)//默认根据扩展名确定文件内容类型，也可以指定
                    .build();
            //将文件上传到mio
            minioClient.uploadObject(uploadObjectArgs);
            log.info("上传文件到minio成功，bucker:{},objectName:{},错误信息：{}", bucket, objectName);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            log.info("上传文件出错，bucker:{},objectName:{},错误信息：{}", bucket, objectName, e.getMessage());
        }
        return false;
    }

    @Override
    public MediaFiles getFileById(String mediaId) {
        MediaFiles mediaFiles = mediaFilesMapper.selectById(mediaId);
        return mediaFiles;
    }

    //获取文件默认存储目录路径 年/月/日
    private String getDefaultFolderPath() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String folder = sdf.format(new Date()).replace("-", "/") + "/";
        //2023-02-17
        //2023/02/17/
        return folder;
    }

    //获取文件的md5
    private String getFileMd5(File file) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            String fileMd5 = DigestUtils.md5Hex(fileInputStream);
            return fileMd5;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public UploadFileResultDto uploadFile(Long companyId, UploadFileParamsDto uploadFileParamsDto, String localFilePath,String objectName) {

        //文件名
        String filename = uploadFileParamsDto.getFilename();
        //先得扩展名
        String extension = filename.substring(filename.lastIndexOf("."));
        //根据扩展名
        // 取出mimeType
        String mimeType = getMimeType(extension);

        //子目录
        String defaultFolderPath = getDefaultFolderPath();
        //文件的md5值
        String fileMd5 = getFileMd5(new File(localFilePath));
        if (StringUtils.isEmpty(objectName)){
            //使用默认的年月日
            objectName = defaultFolderPath + fileMd5 + extension;
        }

        boolean result = addMediaFileToMinIO(localFilePath, mimeType, bucket_mediafiles, objectName);


        if (!result){
            XueChengPlusException.cast("上传文件失败");
        }

        //入库信息
        MediaFiles mediaFiles = currentProxy.addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, bucket_mediafiles, objectName);
        if (mediaFiles==null){
            XueChengPlusException.cast("文件上传后保存信息失败");
        }
        //准备返回的对象
        UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
        BeanUtils.copyProperties(mediaFiles,uploadFileResultDto);

        return uploadFileResultDto;
    }

    /**
     * @description 将文件信息添加到文件表
     * @param companyId  机构id
     * @param fileMd5  文件md5值
     * @param uploadFileParamsDto  上传文件的信息
     * @param bucket  桶
     * @param objectName 对象名称
     * @return com.xuecheng.media.model.po.MediaFiles
     * @author Mr.M
     * @date 2022/10/12 21:22
     */
    @Transactional
    @Override
    public MediaFiles addMediaFilesToDb(Long companyId,String fileMd5,UploadFileParamsDto uploadFileParamsDto,String bucket,String objectName){
        //从数据库查询文件
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles == null) {
            mediaFiles = new MediaFiles();
            //拷贝基本信息
            BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);
            mediaFiles.setId(fileMd5);
            mediaFiles.setFileId(fileMd5);
            mediaFiles.setCompanyId(companyId);
            mediaFiles.setUrl("/" + bucket + "/" + objectName);
            mediaFiles.setBucket(bucket);
            mediaFiles.setFilePath(objectName);
            mediaFiles.setCreateDate(LocalDateTime.now());
            mediaFiles.setAuditStatus("002003");
            mediaFiles.setStatus("1");
            //保存文件信息到文件表
            int insert = mediaFilesMapper.insert(mediaFiles);
            if (insert <= 0) {
                log.error("保存文件信息到数据库失败,{}",mediaFiles.toString());
                XueChengPlusException.cast("保存文件信息失败");
                return null;
            }
            log.debug("保存文件信息到数据库成功,{}",mediaFiles.toString());
        }
        //记录待处理任务
        //通过mimetype判断如果是avi视频才写入待处理任务
        addWaitingTask(mediaFiles);

        //向MediaProcess插入记录
        return mediaFiles;
    }

    /**
     * 添加待处理任务
     * @param mediaFiles
     */
    private void addWaitingTask(MediaFiles mediaFiles){
        //获取文件的mimetype
       //文件名称
        String filename = mediaFiles.getFilename();
        //文件扩展名
        String extension = filename.substring(filename.lastIndexOf("."));
        String mimeType = getMimeType(extension);
        if (mimeType.equals("video/mp4")){ //如果是avi视频写入待处理任务表
            MediaProcess mediaProcess = new MediaProcess();
            BeanUtils.copyProperties(mediaFiles,mediaProcess);
            //状态是未处理
            mediaProcess.setStatus("1");
            mediaProcess.setCreateDate(LocalDateTime.now());
            mediaProcess.setFailCount(0);//失败次数默认为0

            mediaProcessMapper.insert(mediaProcess);
        }
    }

    //检查文件是否存在
    @Override
    public RestResponse<Boolean> checkFile(String fileMd5) {
        //先查询数据库
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles!=null){
            //桶
            String bucket = mediaFiles.getBucket();
            String filePath = mediaFiles.getFilePath();
            //如果数据库存在再查询minio
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath).build();
            //查询远程服务获取到一个流对象
            try {
                FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
                if (inputStream!=null){
                    //文件已存在
                    return RestResponse.success(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //文件不存在
        return RestResponse.success(false);
    }

    //检查分块是否存在
    @Override
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {
        //分块存储路径是：md5前两位为两个目录，chunk存储
        //根据md5得到分块文件的路径
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);

        //如果数据库存在再查询minio
        GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                .bucket(bucket_videofiles)
                .object(chunkFileFolderPath+chunkIndex).build();
        //查询远程服务获取到一个流对象
        try {
            FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
            if (inputStream!=null){
                //文件已存在
                return RestResponse.success(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //文件不存在
        return RestResponse.success(false);
    }

    @Override
    public RestResponse uploadChunk(String fileMd5, int chunk, String localChunkFilePath) {
        //分块文件的路径
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5)+chunk;
        //获取mimetype
        String mimeType = getMimeType(null);
        //将分块文件上传到minio
        boolean b = addMediaFileToMinIO(localChunkFilePath, mimeType, bucket_videofiles, chunkFileFolderPath);
        if (!b){
            return RestResponse.validfail(false,"上传分块文件失败");
        }
        //上传成功
        return RestResponse.success(true);
    }


    //合并文件
    @Override
    public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal, UploadFileParamsDto uploadFileParamsDto) {
        //分块文件路径
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);

        //找到分块文件调用minio的sdk进行文件合并
        List<ComposeSource> sources = Stream.iterate(0, i -> ++i)
                .limit(chunkTotal)
                .map(i -> ComposeSource.builder()
                        .bucket(bucket_videofiles)
                        .object(chunkFileFolderPath+i)
                        .build())
                .collect(Collectors.toList());

        //源文件名称
        String filename = uploadFileParamsDto.getFilename();
        //扩展名
        String extension = filename.substring(filename.lastIndexOf("."));
        //合并后文件的objectname
        String objectname = getFilePathByMd5(fileMd5, extension);
        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
                .bucket(bucket_videofiles)
                .object(objectname)//合并后的文件的objectname
                .sources(sources)
                .build();
        try {
            minioClient.composeObject(composeObjectArgs);
        } catch (Exception e) {
            e.printStackTrace();
            log.info("合并文件出错，bucket:{},objectName:{},错误信息:{}",bucket_videofiles,objectname,e.getMessage());
            return RestResponse.validfail(false,"合并文件异常");
        }
        //校验合并后的文件和源文件是否一致，视频上传才成功
        //先下载文件
        File file = downloadFileFromMinIO(bucket_videofiles, objectname);

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            //计算合并后文件的md5
            String mergeFile_md5 = DigestUtils.md5Hex(fileInputStream);
            //比较原始的md5和合并后的md5
            if (!fileMd5.equals(mergeFile_md5)){
                log.error("校验合并文件md5值不一致,原始文件:{}，合并文件:{}",fileMd5,mergeFile_md5);
                return RestResponse.validfail(false,"文件校验失败");
            }
            //文件的大小
            uploadFileParamsDto.setFileSize(file.length());
        }catch (Exception e){
            return RestResponse.validfail(false,"文件校验失败");
        }

        //将文件信息入库
        MediaFiles mediaFiles = currentProxy.addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, bucket_videofiles, objectname);
        if (mediaFiles==null){
            return RestResponse.validfail(false,"文件入库失败");
        }
        //清理分块文件
        clearChunkFiles(chunkFileFolderPath,chunkTotal);

        return RestResponse.success(true);
    }

    /**
     * 清理分块文件
     * @param chunkFileFolderPath 分块文件路径
     * @param chunkTotal 分块文件总数
     */
    private void clearChunkFiles(String chunkFileFolderPath,int chunkTotal){

        try {
            List<DeleteObject> deleteObjects = Stream.iterate(0, i -> ++i)
                    .limit(chunkTotal)
                    .map(i -> new DeleteObject(chunkFileFolderPath.concat(Integer.toString(i))))
                    .collect(Collectors.toList());

            RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket("video").objects(deleteObjects).build();
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);
            results.forEach(r->{
                DeleteError deleteError = null;
                try {
                    deleteError = r.get();
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("清楚分块文件失败,objectname:{}",deleteError.objectName(),e);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            log.error("清楚分块文件失败,chunkFileFolderPath:{}",chunkFileFolderPath,e);
        }
    }

    /**
     * 从minio下载文件
     * @param bucket 桶
     * @param objectName 对象名称
     * @return 下载后的文件
     */
    public File downloadFileFromMinIO(String bucket,String objectName){
        //临时文件
        File minioFile = null;
        FileOutputStream outputStream = null;
        try{
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            //创建临时文件
            minioFile=File.createTempFile("minio", ".merge");
            outputStream = new FileOutputStream(minioFile);
            IOUtils.copy(stream,outputStream);
            return minioFile;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(outputStream!=null){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private String getChunkFileFolderPath(String fileMd5) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + "chunk" + "/";
    }

    private String getFilePathByMd5(String fileMd5,String fileExt){
        return   fileMd5.substring(0,1) + "/" + fileMd5.substring(1,2) + "/" + fileMd5 + "/" +fileMd5 +fileExt;
    }


}
