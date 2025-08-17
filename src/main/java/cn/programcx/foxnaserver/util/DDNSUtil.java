package cn.programcx.foxnaserver.util;

import cn.programcx.foxnaserver.entity.AccessSecret;
import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.mapper.AccessSecretMapper;
import cn.programcx.foxnaserver.mapper.AccessTaskMapper;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Getter;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DDNSUtil {

    private OkHttpClient client = new OkHttpClient();
    @Autowired
    private AccessTaskMapper accessTaskMapper;

    @Autowired
    private LANIPUtil lanIPUtil;

    @Autowired
    private AccessSecretMapper accessSecretMapper;

    public String getLocalIpv4Ip(boolean isPublicIp) throws Exception {
        if(!isPublicIp) {
            String ip = lanIPUtil.getLocalIPv4();
            if(ip != null && !ip.isEmpty()) {
                return ip;
            }else{
                throw new Exception("无法获取本地IPv4地址，请检查网络连接或配置。");
            }
        }
        Request request = new Request.Builder().url("https://4.ipw.cn/").build();
        String ip;
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                ip = response.body().string();

            } else {
                log.error("无法获取本地IPv4地址，HTTP状态码: {}", response.code());
                throw new Exception("无法获取本地IPv4地址，HTTP状态码: " + response.code());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new Exception("获取本地IPv4地址失败: " + e.getMessage());
        }

        return ip;
    }

    public String getLocalIpv6Ip(boolean isPublicIp) throws Exception {
        if(!isPublicIp) {
            String ip = lanIPUtil.getLocalIPv6();
            if(ip != null && !ip.isEmpty()) {
                return ip;
            }else{
                String name = lanIPUtil.getLocalIPv6();
                throw new Exception("无法获取本地IPv4地址，请检查网络连接或配置。");
            }
        }
        Request request = new Request.Builder().url("https://6.ipw.cn/").build();
        String ip;
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                ip = response.body().string();
            } else {
                log.error("无法获取本地IPv4地址，HTTP状态码: {}", response.code());
                throw new Exception("无法获取本地IPv4地址，HTTP状态码: " + response.code());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new Exception("获取本地IPv4地址失败: " + e.getMessage());
        }

        return ip;
    }


    public static com.aliyun.alidns20150109.Client createAlibabaClient(String AccessId, String AccessSecret) throws Exception {
        Config config = new Config()
                // 您的AccessKey ID，此处仅仅测试，用硬编码
                .setAccessKeyId(AccessId)
                // 您的AccessKey Secret
                .setAccessKeySecret(AccessSecret);
// 访问的域名
        config.endpoint = "alidns.cn-hangzhou.aliyuncs.com";
        return new com.aliyun.alidns20150109.Client(config);
    }

    public void updateDNSIpRecordAlibaba(AccessTask task) throws Exception {
        LambdaQueryWrapper<AccessSecret> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AccessSecret::getId, task.getDnsSecretId());

        AccessSecret accessSecret = accessSecretMapper.selectOne(queryWrapper);

        if (accessSecret == null) {
            log.error("未找到对应的AccessSecret: {}", task.getDnsSecretId());
            throw new Exception("未找到对应的AccessSecret");
        }

        boolean isIpv4 = task.getIpType().equals("ipv4");
        com.aliyun.alidns20150109.Client client = createAlibabaClient(accessSecret.getAccessKey(), accessSecret.getAccessSecret());
        com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest describeDomainRecordsRequest = new com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest();

        describeDomainRecordsRequest.setDomainName(task.getMainDomain());

        //获取该子域所有A记录
        Long PAGE_SIZE = 20L;

        boolean foundRecord = false;

        String ip = "";
        try {
            ip = isIpv4 ? getLocalIpv4Ip(task.getIsPublicIp() != 0) : getLocalIpv6Ip(task.getIsPublicIp() != 0);
            log.info("已获取当前IP地址: {}", ip);
        }catch (Exception e){
            log.error("获取IP地址失败: {}", e.getMessage());
            throw new Exception(e.getMessage());
        }

        try {
            long totalPages = 0L;
            long currentPage = 1L;

            do {
                describeDomainRecordsRequest.setPageNumber(currentPage);
                describeDomainRecordsRequest.setPageSize(PAGE_SIZE);
                describeDomainRecordsRequest.setType(isIpv4 ? "A" : "AAAA");
                DescribeDomainRecordsResponse describeDomainRecordsResponse = client.describeDomainRecords(describeDomainRecordsRequest);
                //查询每页的记录
                List<DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord> recordsMap
                        = describeDomainRecordsResponse.getBody().getDomainRecords().getRecord();
                for (DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord record : recordsMap) {
                    if (
                            task.getDomainRr() != null &&
                                    record.getRR() != null &&
                                    record.getValue() != null &&
                                    record.getRR().equals(task.getDomainRr())
                    ) {
                        if(record.getValue().equals(ip)){
                            foundRecord = true;
                            continue;
                        }
                        com.aliyun.alidns20150109.models.UpdateDomainRecordRequest updateDomainRecordRequest = new com.aliyun.alidns20150109.models.UpdateDomainRecordRequest()
                                .setRecordId(record.getRecordId())
                                .setRR(task.getDomainRr())
                                .setType(isIpv4 ? "A" : "AAAA")
                                .setValue(ip);
                        foundRecord = true;  //设置已找到标识
                        try {
                            client.updateDomainRecord(updateDomainRecordRequest);
                            log.info("已更新DNS记录: {}.{} -> {}", task.getDomainRr(), task.getMainDomain(), ip);
                        } catch (Exception e) {

                            throw new RuntimeException(e);
                        }
                    }
                }

                Long totalCount = describeDomainRecordsResponse.getBody().getTotalCount();

                totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
                currentPage++;
            } while (currentPage <= totalPages);

        } catch (TeaException error) {

            log.error("Error occurred while modifying DNS record: {}", error.getMessage());
            log.warn("Please check the following recommendation: {}", error.getData().get("Recommend"));
            throw new Exception(error.getMessage());
        } catch (Exception _error) {

            TeaException error = new TeaException(_error.getMessage(), _error);
            log.error("Error occurred while modifying DNS record: {}", error.getMessage());
            log.warn("Please check the following recommendation: {}", error.getData().get("Recommend"));
            throw new Exception(error.getMessage());
        }

        // 如果没有找到记录，则添加新的A记录
        if (!foundRecord) {
            com.aliyun.alidns20150109.models.AddDomainRecordRequest addDomainRecordRequest = new com.aliyun.alidns20150109.models.AddDomainRecordRequest()
                    .setDomainName(task.getMainDomain())
                    .setRR(task.getDomainRr())
                    .setType(isIpv4 ? "A" : "AAAA")
                    .setValue(ip)
                    .setTTL(600L);
            try {
                client.addDomainRecord(addDomainRecordRequest);
                log.info("已添加新的DNS记录: {}.{} -> {}", task.getDomainRr(), task.getMainDomain(), ip);
            } catch (Exception e) {
                log.error("Error occurred while adding DNS record: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        modifyDNSTaskIp(ip,task);
    }

    public void modifyDNSTaskIp(String ip,AccessTask task) {
        task.setTaskIp(ip);
        accessTaskMapper.updateById(task);
    }

}
