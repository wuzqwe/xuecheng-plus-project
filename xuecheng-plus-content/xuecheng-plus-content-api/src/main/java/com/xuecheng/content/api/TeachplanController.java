package com.xuecheng.content.api;

import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDao;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程计划管理相关的接口
 */

@Api(value = "课程计划编辑接口",tags = "课程计划编辑接口")
@RestController
public class TeachplanController {

    @Autowired
    private TeachplanService teachplanService;
    //查询课程计划
    @ApiOperation("查询课程计划树形结构")
    @GetMapping("/teachplan/{courseId}/tree-nodes")
    public List<TeachplanDto> getTreeNodes(@PathVariable long courseId){
        return teachplanService.findTeachplanTree(courseId);
    }

    @ApiOperation("课程计划创建或修改")
    @PostMapping("/teachplan")
    public void saveTeachplan( @RequestBody SaveTeachplanDao teachplan){
        teachplanService.saveTeachplan(teachplan);
    }

    @ApiOperation("删除课程信息")
    @DeleteMapping("/teachplan/{id}")
    public void deleteTeachplan(@PathVariable Long id){
        teachplanService.deleteTeachplan(id);
    }

    @ApiOperation(value = "课程计划和媒资信息绑定")
    @PostMapping("/teachplan/association/media")
    public TeachplanMedia associationMedia(@RequestBody BindTeachplanMediaDto bindTeachplanMediaDto){
        return teachplanService.associationMedia(bindTeachplanMediaDto);
    }
}
