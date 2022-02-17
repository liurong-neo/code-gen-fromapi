package com.fzm.maven.plugin.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class GitLabAPIUtil {

    /** 获取指定项目的projectId */
    private static String GITLAB_SINGLE_PROJECT_API = "https://#{REPO_IP}/api/v4/projects/#{PROJECT_PATH}?private_token=#{PRIVATE_TOKEN}";

    /** 获取gitlab的文件内容 */
    private static String GITLAB_FILE_CONTENT_API = "https://#{REPO_IP}/api/v4/projects/#{PROJECT_ID}/repository/files/#{FILE_PATH}?ref=#{BRANCH_NAME}";


    /**
     * 使用gitLab api获取指定项目的projectId，为Get请求
     * @param ip  项目仓库的ip地址
     * @param projectPath   项目的path
     * @param privateToken  访问gitlab库时的privateToken
     * @return  返回目的projectId
     */
    private static String getProjectId(String ip, String projectPath, String privateToken) {
        /** 1.参数准备，存入map */
        Map<String, Object> params = new HashMap<>(4);
        params.put("REPO_IP", ip);
        params.put("PRIVATE_TOKEN", privateToken);
        // gitlab api要求项目的path需要安装uri编码格式进行编码
        try {
            params.put("PROJECT_PATH", URLEncoder.encode(projectPath, String.valueOf(Consts.UTF_8)));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(String.format("对%s进行URI编码出错！", projectPath));
        }
        // 调用工具类替换,得到具体的调用地址
        String getSingleProjectUrl = PlaceholderUtil.anotherReplace(GITLAB_SINGLE_PROJECT_API, params);
        //  创建URI对象
        URI url = null;
        try {
            url = new URI(getSingleProjectUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("使用%s创建URI出错！", getSingleProjectUrl));
        }
        /** 2.访问url，获取制定project的信息 */
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> result = restTemplate.getForEntity(url, String.class);
        /** 3.解析结果 */
        if (result.getStatusCode() != HttpStatus.OK ) {
            throw new RuntimeException(String.format("请求%s出错！错误码为：%s", url, result.getStatusCode()));
        }
        JSONObject responseBody = JSON.parseObject(result.getBody());
        String projectId = responseBody.getString("id");
        /** 4.返回projectId */
        return projectId;
    }

    public static String getFileContentFromRepository(String ip, String projectPath, String fileFullPath, String branchName, String privateToken) throws Exception{
        if(StringUtils.isEmpty(branchName)){
            branchName = "master";
        }
        /** 2.使用privateToken获取项目的projectId */
        String projectId = getProjectId(ip, projectPath, privateToken);
        /** 3.使用参数替换形成请求git库中文件内容的uri */
        //  参数准备，存入map
        Map<String, Object> params = new HashMap<>();
        params.put("REPO_IP", ip);
        params.put("PROJECT_ID", projectId);
        params.put("PRIVATE_TOKEN", privateToken);
        try {
            params.put("FILE_PATH", URLEncoder.encode(fileFullPath, String.valueOf(Consts.UTF_8)));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        params.put("BRANCH_NAME", branchName);
        //  使用工具类替换参数
        String reqFileContentUri = PlaceholderUtil.anotherReplace(GITLAB_FILE_CONTENT_API, params);
        /** 4.请求gitlab获取文件内容 */
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("PRIVATE-TOKEN", privateToken);
        //RestTemplate 会再次对 URL 进行编码, 可以使用方法来判断URI已经编码
        URI gitlabUri = UriComponentsBuilder.fromHttpUrl(reqFileContentUri).build(true).toUri();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response ;
        try {
            response = restTemplate.exchange(gitlabUri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
        }catch(Exception e){
            throw  e;
        }
        /** 5.解析响应结果内容 */
        String body = response.getBody();
        JSONObject jsonBody = JSON.parseObject(body);
        String content = new String(Base64.getDecoder().decode(jsonBody.getString("content")), Consts.UTF_8);
        /** 6.返回内容 */
        return content;
    }

}
