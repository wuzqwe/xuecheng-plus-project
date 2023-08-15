package com.xuecheng.content.service.impl;

import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.model.dto.CourseCategoryTreeDto;
import com.xuecheng.content.service.CourseCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2023/2/12 14:49
 */
@Slf4j
@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    @Autowired
    CourseCategoryMapper courseCategoryMapper;

    @Override
    public List<CourseCategoryTreeDto> queryTreeNodes(String id) {
        List<CourseCategoryTreeDto> courseCategoryTreeDtos = courseCategoryMapper.selectTreeNodes(id);

        //找到每一个节点的子节点，最终封装成List<CourseCategoryTreeDto>
        //先将list转成map.key就是结点的id，value就是CourseCategoryTreeDto对象，目的就是为了方便从map获取结点
        Map<String, CourseCategoryTreeDto> map = courseCategoryTreeDtos.stream()
                .filter(item->!id.equals(item.getId()))
                .collect(Collectors.toMap(key -> key.getId(), value -> value, (key1, key2) -> key2));
        //定义一个list最终返回的list
        ArrayList<CourseCategoryTreeDto> courseCategoryList = new ArrayList<>();
        //从头遍历List<CourseCategoryTreeDto>
        courseCategoryTreeDtos.stream().filter(item->!id.equals(item.getId())).forEach(item->{
            //向list写入数据
            if(item.getParentid().equals(id)){
                courseCategoryList.add(item);
            }
           //找到节点的父节点
            CourseCategoryTreeDto courseCategoryTreeDto = map.get(item.getParentid());
            if(courseCategoryTreeDto!=null){
                if(courseCategoryTreeDto.getChildrenTreeNodes()==null){
                    //如果该父节点的属性childrenTreeNodes为空，要new一个集合，因为要向集合添加它的子节点
                    courseCategoryTreeDto.setChildrenTreeNodes(new ArrayList<CourseCategoryTreeDto>());
                }
                //找到每个节点的子节点放在父节点的childrenTreeNodes属性中
                courseCategoryTreeDto.getChildrenTreeNodes().add(item);
            }


        });
        return courseCategoryList;
    }
}
