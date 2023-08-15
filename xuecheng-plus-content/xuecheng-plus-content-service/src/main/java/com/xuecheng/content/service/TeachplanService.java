package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDao;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.TeachplanMedia;

import java.util.List;

public interface TeachplanService {

    /**
     * 根据课程id查询课程计划
     * @param courseId
     * @return
     */
    public List<TeachplanDto> findTeachplanTree(Long courseId);

    /**
     * 新增/修改/保存课程计划
     * @param saveTeachplanDao
     */
    public void saveTeachplan(SaveTeachplanDao saveTeachplanDao);

    /**
     * 删除课程
     * @param id
     */
    public void deleteTeachplan(Long id);

    /**
     * 教学计划绑定媒资
     * @param bindTeachplanMediaDto
     */
    public TeachplanMedia associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);
}
