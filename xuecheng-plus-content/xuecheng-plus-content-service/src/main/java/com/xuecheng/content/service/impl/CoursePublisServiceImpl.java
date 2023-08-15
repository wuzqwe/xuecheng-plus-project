package com.xuecheng.content.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.CommonError;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.utils.StringUtil;
import com.xuecheng.content.config.MultipartSupportConfig;
import com.xuecheng.content.feignclient.MediaServiceClient;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.mapper.CoursePublishPreMapper;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.model.po.CoursePublishPre;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.content.service.TeachplanService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import freemarker.core.ParseException;
import freemarker.template.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CoursePublisServiceImpl implements CoursePublishService {

    @Autowired
    private CourseBaseInfoService courseBaseInfoService;

    @Autowired
    private TeachplanService teachplanService;

    @Autowired
    private CourseMarketMapper courseMarketMapper;

    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CoursePublishMapper coursePublishMapper;

    @Autowired
    private MqMessageService mqMessageService;

    @Autowired
    private MediaServiceClient mediaServiceClient;

    @Autowired
    RedisTemplate redisTemplate;


    @Override
    @Transactional
    public void commitAudit(Long companyId, Long courseId) {

        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        if (courseBaseInfo==null){
            XueChengPlusException.cast("课程找不到");
        }
        //审核状态
        String auditStatus = courseBaseInfo.getAuditStatus();

        //如果课程的审核状态为已提交则不允许提交
        if (auditStatus.equals("202003")){
            XueChengPlusException.cast("课程已提交请等待审核");
        }
        //课程的图片、计划信息没有填写也不允许提交
        String pic = courseBaseInfo.getPic();
        if (StringUtil.isEmpty(pic)){
            XueChengPlusException.cast("请求上传课程图片");
        }
        //查询课程计划
        List<TeachplanDto> teachplanTree=teachplanService.findTeachplanTree(courseId);
        if (teachplanTree==null||teachplanTree.size()==0){
            XueChengPlusException.cast("请编写课程计划");
        }
        //查询到课程基本信息，营销信息、计划信息插入到课程预发布表
        CoursePublishPre coursePublishPre = new CoursePublishPre();
        BeanUtils.copyProperties(courseBaseInfo,coursePublishPre);
        //设置机构的id
        coursePublishPre.setCompanyId(companyId);
        //本机构只能提交本机构的课程
        Long companyId1 = courseBaseInfo.getCompanyId();
        if(!companyId.equals(companyId1)){
            XueChengPlusException.cast("本机构只能提交本机构的课程");
        }

        //营销信息
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        //转json
        String courseMarketJson = JSON.toJSONString(courseMarket);
        coursePublishPre.setMarket(courseMarketJson);
        //计划信息
        String teachPlanJson = JSON.toJSONString(teachplanTree);
        coursePublishPre.setTeachplan(teachPlanJson);
        //状态为已提交
        coursePublishPre.setStatus("202003");
        //提交时间
        coursePublishPre.setCreateDate(LocalDateTime.now());

        //查询预发布表，如果有记录则更新，没有则插入
        CoursePublishPre coursePublishObj = coursePublishPreMapper.selectById(courseId);
        if (coursePublishObj==null){
            //插入
            coursePublishPreMapper.insert(coursePublishPre);
        }else {
            //更新
            coursePublishPreMapper.updateById(coursePublishPre);
        }


        //更新课程基本信息表的审核状态为已提交
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        courseBase.setAuditStatus("202003");//审核状态为已提交
        courseBaseMapper.updateById(courseBase);

    }

    @Override
    @Transactional
    public void publish(Long companyId, Long courseId) {
        //课程如果没有审核通过不允许发布

        //查询预发布表
        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPre==null){
            XueChengPlusException.cast("课程没有审核记录，无法发布");
        }

        //状态
        String status = coursePublishPre.getStatus();
        if (!status.equals("202004")){
            XueChengPlusException.cast("课程没有审核通过不允许发布");
        }

        //向课程发布表写入数据
        CoursePublish coursePublish = new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre,coursePublish);

        CoursePublish coursePublish1 = coursePublishMapper.selectById(courseId);
        if (coursePublish1==null){
            coursePublishMapper.insert(coursePublish);
        }else {
            coursePublishMapper.updateById(coursePublish);
        }

        //向消息表写入数据
        //todo
        MqMessage mqMessage = mqMessageService.addMessage("course_publish", String.valueOf(courseId), null, null);
        if (mqMessage==null){
            XueChengPlusException.cast(CommonError.UNKOWN_ERROR);
        }

        //将预发布表数据删除
        coursePublishPreMapper.deleteById(courseId);
    }

    @Override
    public CoursePreviewDto getCoursePreviewInfo(Long courseId) {
        //课程基本信息、营销信息
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);

        //课程计划信息
        List<TeachplanDto> teachplanTree= teachplanService.findTeachplanTree(courseId);

        CoursePreviewDto coursePreviewDto = new CoursePreviewDto();
        coursePreviewDto.setCourseBase(courseBaseInfo);
        coursePreviewDto.setTeachplans(teachplanTree);
        return coursePreviewDto;
    }

    @Override
    public File generateCourseHtml(Long courseId) {
        Configuration configuration = new Configuration(Configuration.getVersion());
        //最终的静态文件
        File htmlFile = null;
        try {
            //拿到classpath路径
            String classpath = this.getClass().getResource("/").getPath();
            //指定模板的目录
            configuration.setDirectoryForTemplateLoading(new File(classpath+"/templates/"));
            //指定编码
            configuration.setDefaultEncoding("utf-8");

            //得到模板
            Template template = configuration.getTemplate("course_template.ftl");
            //准备数据
            CoursePreviewDto coursePreviewInfo = this.getCoursePreviewInfo(courseId);
            HashMap<String, Object> map = new HashMap<>();
            map.put("model",coursePreviewInfo);

            //Template template 模板, Object model 数据
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
            //输入流
            InputStream inputStream = IOUtils.toInputStream(html, "utf-8");
            htmlFile = File.createTempFile("coursepublish",".html");
            //输出文件
            FileOutputStream outputStream = new FileOutputStream(htmlFile);
            //使用流将html写入文件
            IOUtils.copy(inputStream,outputStream);
        }catch (Exception ex){
            log.error("页面静态化出现问题,课程id:{}",courseId,ex);
            ex.printStackTrace();
        }

        return htmlFile;
    }

    @Override
    public void uploadCourseHtml(Long courseId, File file) {
        try {
            //将file转成MultipartFile
            MultipartFile multipartFile = MultipartSupportConfig.getMultipartFile(file);
            //远程调用得到返回值
            String upload = mediaServiceClient.upload(multipartFile, "course/"+courseId+".html");
            if(upload==null){
                log.debug("远程调用走降级逻辑得到上传的结果为null,课程id:{}",courseId);
                XueChengPlusException.cast("上传静态文件过程中存在异常");
            }
        }catch (Exception ex){
            ex.printStackTrace();
            XueChengPlusException.cast("上传静态文件过程中存在异常");
        }
    }

    @Override
    public CoursePublish getCoursePublish(Long courseId) {
        CoursePublish coursePublish = coursePublishMapper.selectById(courseId);
        return coursePublish ;
    }

    @Override
    public CoursePublish getCoursePublishCache(Long courseId) {
        Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
        if ("null".equals(jsonObj)){
            return null;
        }
        if (jsonObj!=null){
            String jsonString = jsonObj.toString();
            CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
            return coursePublish;
        }else {
            //从数据库查询
            System.out.println("//从数据库查询");
            CoursePublish coursePublish = getCoursePublish(courseId);

//            if (coursePublish!=null){
                redisTemplate.opsForValue().set("course:"+courseId,JSON.toJSONString(coursePublish),30+new Random().nextInt(100), TimeUnit.SECONDS);
//            }
            return coursePublish;
        }
    }
}
