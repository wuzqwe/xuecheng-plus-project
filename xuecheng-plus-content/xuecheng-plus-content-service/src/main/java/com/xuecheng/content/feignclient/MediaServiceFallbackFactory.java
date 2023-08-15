package com.xuecheng.content.feignclient;

import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
@Slf4j
public class MediaServiceFallbackFactory implements FallbackFactory<MediaServiceClient> {
    @Override
    public MediaServiceClient create(Throwable throwable) {
        return new MediaServiceClient() {
            @Override
            public String upload(MultipartFile filedata, String objectName) throws IOException {
                //降级方法
                log.debug("调用媒资管理服务上传文件时发生熔断，异常信息：{}", throwable.toString(), throwable);
                return null;
            }
        };
    }
}
