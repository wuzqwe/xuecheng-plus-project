package com.xuecheng.learning.service.ImpI;

import com.xuecheng.base.model.RestResponse;
import com.xuecheng.base.utils.StringUtil;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.learning.feignclient.ContentServiceClient;
import com.xuecheng.learning.feignclient.MediaServiceClient;
import com.xuecheng.learning.model.dto.XcCourseTablesDto;
import com.xuecheng.learning.service.LearningService;
import com.xuecheng.learning.service.MyCourseTableService;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LearningServiceImpl implements LearningService {

    @Autowired
    MyCourseTableService myCourseTableService;

    @Autowired
    private ContentServiceClient contentServiceClient;

    @Autowired
    private MediaServiceClient mediaServiceClient;

    @Override
    public RestResponse<String> getVideo(String userId,Long courseId, Long teachplanId, String mediaId) {
        //如果用户没有登录
        CoursePublish coursepublish = contentServiceClient.getCoursepublish(courseId);
        if (coursepublish==null) {
            return RestResponse.validfail("课程不存在");
        }

        //获取学习资格
        if (StringUtils.isNoneEmpty(userId)){
            //用户登录
            XcCourseTablesDto learningStatus = myCourseTableService.getLearningStatus(userId, courseId);
            String learnStatus = learningStatus.getLearnStatus();
            if ("702002".equals(learnStatus)){
                return RestResponse.validfail("无法学习，因为没有选课或选课后没有支付");
            } else if ("702003".equals(learnStatus)) {
                return RestResponse.validfail("已过期需要申请续期或重新支付");
            }else {
                //有资格学习，要返回视频播放地址
                //远程调用媒资获取视频播放地址
                RestResponse<String> playUrlByMediaId = mediaServiceClient.getPlayUrlByMediaId(mediaId);
                return playUrlByMediaId;
            }

        }
        //如果用户没有登录
        //取出课程的收费规则
        String charge = coursepublish.getCharge();
        if ("201000".equals(charge)){
            //远程调用媒资获取视频播放地址
            RestResponse<String> playUrlByMediaId = mediaServiceClient.getPlayUrlByMediaId(mediaId);
            return playUrlByMediaId;
        }
        return RestResponse.validfail("该课程没有选课");
    }
}
