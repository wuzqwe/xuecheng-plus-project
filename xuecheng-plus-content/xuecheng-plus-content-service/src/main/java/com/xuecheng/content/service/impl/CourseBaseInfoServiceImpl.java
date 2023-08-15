package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.model.dto.AddCourseDto;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.EditCourseDto;
import com.xuecheng.content.model.dto.QueryCourseParamsDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseCategory;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.service.CourseBaseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resources;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2023/2/12 10:16
 */
@Slf4j
@Service
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {
    @Autowired
    CourseBaseMapper courseBaseMapper;

    @Autowired
    CourseMarketMapper courseMarketMapper;

    @Autowired
    CourseCategoryMapper courseCategoryMapper;

    @Override
    public PageResult<CourseBase> queryCourseBaseList(Long companyId,PageParams pageParams, QueryCourseParamsDto courseParamsDto) {

        //拼装查询条件
        LambdaQueryWrapper<CourseBase> queryWrapper = new LambdaQueryWrapper<>();
        //根据名称模糊查询,在sql中拼接 course_base.name like '%值%'
        queryWrapper.like(StringUtils.isNotEmpty(courseParamsDto.getCourseName()),CourseBase::getName,courseParamsDto.getCourseName());
        //根据课程审核状态查询 course_base.audit_status = ?
        queryWrapper.eq(StringUtils.isNotEmpty(courseParamsDto.getAuditStatus()), CourseBase::getAuditStatus,courseParamsDto.getAuditStatus());
        //todo:按课程发布状态查询
        //根据培训机构的id拼接查询条件
        queryWrapper.eq(CourseBase::getCompanyId,companyId);

        queryWrapper.eq(StringUtils.isNotEmpty(courseParamsDto.getPublishStatus()),CourseBase::getStatus,courseParamsDto.getPublishStatus());
        //创建page分页参数对象，参数：当前页码，每页记录数
        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        //开始进行分页查询
        Page<CourseBase> pageResult = courseBaseMapper.selectPage(page, queryWrapper);
        //数据列表
        List<CourseBase> items = pageResult.getRecords();
        //总记录数
        long total = pageResult.getTotal();

        //List<T> items, long counts, long page, long pageSize
        PageResult<CourseBase> courseBasePageResult = new PageResult<CourseBase>(items,total,pageParams.getPageNo(), pageParams.getPageSize());
        return  courseBasePageResult;
    }

    @Transactional
    @Override
    public CourseBaseInfoDto createCourseBase(Long companyId, AddCourseDto dto) {
        //参数的合法性校验
      /*  if (StringUtils.isBlank(dto.getName())) {
            throw new XueChengPlusException("课程名称为空");
        }

        if (StringUtils.isBlank(dto.getMt())) {
            throw new XueChengPlusException("课程分类为空");
        }

        if (StringUtils.isBlank(dto.getSt())) {
            throw new XueChengPlusException("课程分类为空");
        }

        if (StringUtils.isBlank(dto.getGrade())) {
            throw new XueChengPlusException("课程等级为空");
        }

        if (StringUtils.isBlank(dto.getTeachmode())) {
            throw new XueChengPlusException("教育模式为空");
        }

        if (StringUtils.isBlank(dto.getUsers())) {
            throw new XueChengPlusException("适应人群");
        }

        if (StringUtils.isBlank(dto.getCharge())) {
            throw new XueChengPlusException("收费规则为空");
        }

        if(dto.getCharge().equals("201001")){
            if(dto.getPrice() ==null || dto.getPrice().floatValue()<=0){
                throw new XueChengPlusException("课程的价格不能为空并且必须大于0");
            }
        }*/
        //向课程及信息表course_base写入数据
        CourseBase courseBase = new CourseBase();
        //将传入的页面的参数到courseBaseNew对象
        /* courseBase.setName(dto.getName());
        courseBase.setDescription(dto.getDescription());*/
        //上面的从原始对象中get拿到数据向新对象set比较复杂
        BeanUtils.copyProperties(dto,courseBase);//只要属性名称一致就可以拷贝
        courseBase.setCompanyId(companyId);
        courseBase.setCreateDate(LocalDateTime.now());
        //审核状态默认为未提交
        courseBase.setAuditStatus("202002");
        //发布状态为未发布
        courseBase.setStatus("203001");

        //插入数据
        int insert = courseBaseMapper.insert(courseBase);
        if (insert<=0){
            throw new RuntimeException("添加课程失败");
        }
        //向课程营销表coures_market写入数据
        CourseMarket courseMarket = new CourseMarket();

        BeanUtils.copyProperties(dto,courseMarket);
        //课程id
        Long courseBaseId = courseBase.getId();
        courseMarket.setId(courseBaseId);

        //保存营销信息
        int i = saveCourseMark(courseMarket);
        if (i<=0){
            throw new RuntimeException("保存营销信息失败");
        }

        //从数据库查询课程的详细信息
        CourseBaseInfoDto courseBaseInfo = getCourseBaseInfo(courseBaseId);

        return courseBaseInfo;
    }


    @Override
    @Transactional
    public CourseBaseInfoDto updateCourseBase(Long companyId, EditCourseDto editCourseDto) {

        Long courseId = editCourseDto.getId();
        //查询课程信息
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if(courseBase==null){
            XueChengPlusException.cast("课程不存在");
        }
        //数据的合法性校验
        //根据具体的业务逻辑去校验
        //本机构只能修改本机构的课程
        if (!companyId.equals(courseBase.getCompanyId())){
            XueChengPlusException.cast("本机构只能修改本机构的课程");
        }

        //封装数据
        BeanUtils.copyProperties(editCourseDto,courseBase);
        courseBase.setCompanyId(companyId);
        courseBase.setChangeDate(LocalDateTime.now());

        CourseMarket courseMarket = new CourseMarket();
        BeanUtils.copyProperties(editCourseDto,courseMarket);


        //更新数据库
        int i = courseBaseMapper.updateById(courseBase);
        int i1 = saveCourseMark(courseMarket);
        if (i<=0||i1<=0){
            XueChengPlusException.cast("修改课程失败");
        }
        //查询课程信息
        CourseBaseInfoDto courseBaseInfo = getCourseBaseInfo(courseId);

        return courseBaseInfo;
    }


    //查询课程信息
    @Override
    public CourseBaseInfoDto getCourseBaseInfo(Long courseId) {
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if (courseBase==null){
            return null;
        }
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);

        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(courseBase,courseBaseInfoDto);
        if(courseMarket!=null)
            BeanUtils.copyProperties(courseMarket,courseBaseInfoDto);

        //通过courseCategoryMapper查询分类信息，将分类名称放在courseBaseInfoDto对象
        //todo:将课程分类放在courseBaseInfoDto
        CourseCategory courseCategory = courseCategoryMapper.selectById(courseBase.getMt());
        courseBaseInfoDto.setMtName(courseCategory.getName());
        CourseCategory courseCategory1 = courseCategoryMapper.selectById(courseBase.getSt());
        courseBaseInfoDto.setStName(courseCategory1.getName());

        return courseBaseInfoDto;
    }

    //单独写一个方法保存营销信息，逻辑，存在则更新，不存在则添加
    private int saveCourseMark(CourseMarket courseMarket){

        //参数合法性校验
        String charge = courseMarket.getCharge();
        if (StringUtils.isEmpty(charge)){
            throw new RuntimeException("收费规则为空");
        }

        //如果课程收费，价格没有填写也需要抛出异常
        if(charge.equals("201001")){
            if (courseMarket.getPrice()==null||courseMarket.getPrice().floatValue()<=0){
                throw new RuntimeException("收费规则为空");
            }
        }

        //从数据库查询课程信息，存在则更新，不存在则添加
        Long id = courseMarket.getId();//主键
        CourseMarket courseMarket1 = courseMarketMapper.selectById(id);
        if(courseMarket1==null){
            //插入数据库更新
            int insert = courseMarketMapper.insert(courseMarket);
            return insert;
        }else {
            //将courseMarket拷贝到courseMarket1
            BeanUtils.copyProperties(courseMarket,courseMarket1);
            courseMarket1.setId(courseMarket1.getId());
            //更新
            int i = courseMarketMapper.updateById(courseMarket1);
            return i;
        }
    }


}

