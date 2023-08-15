package com.xuecheng.content;

import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.service.CoursePublishService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.io.IOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * 测试freemark页面静态化方法
 */
@SpringBootTest
public class FreemarkerTest {

    @Autowired
    private CoursePublishService coursePublishService;

    @Test
    void testGenerateHtmlByTemplate() throws Exception{

        Configuration configuration = new Configuration(Configuration.getVersion());
        //拿到classpath路径
        String classpath = this.getClass().getResource("/").getPath();
        //指定模版目录
        configuration.setDirectoryForTemplateLoading(new File(classpath+"/templates/"));
        //指定编码
        configuration.setDefaultEncoding("utf-8");
        //得到模版
        Template template = configuration.getTemplate("course_template.ftl");
        //准备数据
        CoursePreviewDto coursePreviewDto=coursePublishService.getCoursePreviewInfo(120L);

        Map<String,Object> map=new HashMap<>();
        map.put("model",coursePreviewDto);

        String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
        System.out.println(content);
        //将静态文件输出到文件中
        InputStream inputStream = IOUtils.toInputStream(content);
        //输出流
        FileOutputStream fileOutputStream = new FileOutputStream("D:\\upload\\120.html");
        IOUtils.copy(inputStream,fileOutputStream);
    }
}
