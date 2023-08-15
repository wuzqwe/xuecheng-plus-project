package com.xuecheng.content.api;

import com.alibaba.fastjson.JSON;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.service.CoursePublishService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

@RestController
@Slf4j
public class CoursePublishController {

    @Autowired
    private CoursePublishService coursePublishService;

    @ResponseBody
    @PostMapping("/courseaudit/commit/{courseId}")
    public void commitAudit(@PathVariable("courseId") Long courseId){
        Long compayId=1232141425L;
        coursePublishService.commitAudit(compayId,courseId);
    }

    @ApiOperation("课程发布")
    @ResponseBody
    @PostMapping ("/coursepublish/{courseId}")
    public void coursepublish(@PathVariable("courseId") Long courseId){
        Long compayId=1232141425L;
        coursePublishService.publish(compayId,courseId);
    }

    @GetMapping("/coursepreview/{courseId}")
    public ModelAndView preview(@PathVariable("courseId") Long courseId){

        ModelAndView modelAndView = new ModelAndView();
        //查询课程的信息作为模型数据
        CoursePreviewDto coursePreviewInfo = coursePublishService.getCoursePreviewInfo(courseId);
        //指定模型
        modelAndView.addObject("model", coursePreviewInfo);
        //指定模板
        modelAndView.setViewName("course_template");//根据视图名称加.ftl找到模板
        return modelAndView;
    }

    @ApiOperation("查询课程发布信息")
    @ResponseBody
    @GetMapping("/course/whole/{courseId}")
    public CoursePreviewDto getCoursepublish1(@PathVariable("courseId") Long courseId) {
        CoursePreviewDto coursePreviewDto = new CoursePreviewDto();

//        CoursePublish coursePublish = coursePublishService.getCoursePublish(courseId);
        CoursePublish coursePublish = coursePublishService.getCoursePublishCache(courseId);
        if (coursePublish==null){
            return coursePreviewDto;
        }
        //开始向coursePreviewDto填充数据
        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(coursePublish,courseBaseInfoDto);

        //课程计划信息
        String teachplanJson = coursePublish.getTeachplan();
        //转成List<Teachplan>
        List<TeachplanDto> teachplanDtos = JSON.parseArray(teachplanJson, TeachplanDto.class);
        coursePreviewDto.setCourseBase(courseBaseInfoDto);
        coursePreviewDto.setTeachplans(teachplanDtos);


        return coursePreviewDto;
    }

    @ApiOperation("查询课程发布信息")
    @ResponseBody
    @GetMapping("/r/coursepublish/{courseId}")
    public CoursePublish getCoursepublish(@PathVariable("courseId") Long courseId) {
     return coursePublishService.getCoursePublish(courseId);
    }


}
