package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.mapper.TeachplanMediaMapper;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDao;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TeachplanServiceImpl implements TeachplanService {

  @Autowired
  TeachplanMapper teachplanMapper;

  @Autowired
  private TeachplanMediaMapper teachplanMediaMapper;

  @Override
  public List<TeachplanDto> findTeachplanTree(Long courseId) {
    return teachplanMapper.selectTreeNodes(courseId);
 }

    @Override
    public void saveTeachplan(SaveTeachplanDao saveTeachplanDao) {
        Long teachplanId = saveTeachplanDao.getId();
        if(teachplanId==null){
            //新增
            Teachplan teachplan = new Teachplan();
            BeanUtils.copyProperties(saveTeachplanDao,teachplan);
            //确定排序的字段，找到它的同级节点个数，排序字段就是个数，排序字段就是个数加1
            //select count(1) from teachplan where course_id=117 and parentid=28
            Long parentid = saveTeachplanDao.getParentid();
            Long courseId = saveTeachplanDao.getCourseId();

            int teachplanCount = getTeachplanCount(courseId, parentid);
            teachplan.setOrderby(teachplanCount);

            teachplanMapper.insert(teachplan);
        }else {
            //修改
            Teachplan teachplan = teachplanMapper.selectById(teachplanId);
            //将参数复制到teachplan
            BeanUtils.copyProperties(saveTeachplanDao,teachplan);
            teachplanMapper.updateById(teachplan);
        }
    }

    @Override
    @Transactional
    public void deleteTeachplan(Long id) {
        Teachplan teachplan = teachplanMapper.selectById(id);
        if (teachplan.getParentid()==0){
            LambdaQueryWrapper<Teachplan> queryWrapper=new LambdaQueryWrapper<>();
            queryWrapper.eq(id!=0,Teachplan::getParentid,id);
            Integer count = teachplanMapper.selectCount(queryWrapper);
            if(count<=0){
                int count1 = teachplanMapper.deleteById(id);
                if (count1<=0){
                    XueChengPlusException.cast("删除章节失败");
                }
            }else {
                XueChengPlusException.cast("课程计划信息还有子级信息，无法操作");
            }
        }else {
            int i = teachplanMapper.deleteById(id);
            if(i<=0){
                XueChengPlusException.cast("删除失败");
            }else {
                LambdaQueryWrapper<TeachplanMedia> queryWrapper=new LambdaQueryWrapper<>();
                queryWrapper.eq(TeachplanMedia::getTeachplanId,id);
                int delete = teachplanMediaMapper.delete(queryWrapper);
                if (delete<0){
                    XueChengPlusException.cast("删除失败");
                }
            }
        }
    }

    @Override
    @Transactional
    public TeachplanMedia  associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        //教学计划id
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if(teachplan==null){
            XueChengPlusException.cast("教学计划不存在");
        }
        Integer grade = teachplan.getGrade();
        if(grade!=2){
            XueChengPlusException.cast("只允许第二级教学计划绑定媒资文件");
        }
        //课程id
        Long courseId = teachplan.getCourseId();

        //先删除原来该教学计划绑定的媒资
        teachplanMediaMapper.delete(new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId,teachplanId));

        //再添加教学计划与媒资的绑定关系
        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setCourseId(courseId);
        teachplanMedia.setTeachplanId(teachplanId);
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setCreateDate(LocalDateTime.now());
        teachplanMediaMapper.insert(teachplanMedia);
        return teachplanMedia;
    }


    private int getTeachplanCount(Long courseId,Long parentId){
        LambdaQueryWrapper<Teachplan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Teachplan::getCourseId,courseId).eq(Teachplan::getParentid,parentId);
        Integer count = teachplanMapper.selectCount(queryWrapper);
        return count+1;
    }
}