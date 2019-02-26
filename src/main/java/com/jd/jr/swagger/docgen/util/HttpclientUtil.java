package com.jd.jr.swagger.docgen.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

public class HttpclientUtil {
	public static String executeHttpPost(String url, Map<String, String> params) throws Exception {
		HttpClient httpClient = new HttpClient();
		PostMethod postMethod = new PostMethod(url);
		NameValuePair[] data = getData(params);
		postMethod.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "UTF-8");
		postMethod.setRequestBody(data);
		String result = "";
		try {
			int statusCode = httpClient.executeMethod(postMethod);
			System.out.println("executeHttpPost|" + statusCode);
			InputStream in = postMethod.getResponseBodyAsStream();
			BufferedReader is = null;
			try {
				is = new BufferedReader(new InputStreamReader(in, "UTF-8"));
				String line;
				while ((line = is.readLine()) != null) {
					result += line;
				}
			} catch (Exception e) {
				System.out.println("executeHttpPost|result:" + e.getMessage());
			}
			System.out.println("executeHttpPost|result:" + result);
		} catch (Exception e) {
			System.out.println("executeHttpPost|error:" + e.getMessage());
		}
		return result;
	}

	private static NameValuePair[] getData(Map<String, String> map) {
		NameValuePair[] data = new NameValuePair[map.keySet().size()];
		int i = 0;
		for (String set : map.keySet()) {
			data[i] = new NameValuePair(set, map.get(set));
			i++;
		}
		return data;
	}

	public static void main(String[] args) throws Exception {
		String swaggerJson = "{\"swagger\":\"2.0\",\"info\":{\"description\":\"This is a sample for swagger-maven-plugin\",\"version\":\"v1\",\"title\":\"Swagger Maven Plugin Sample\"},\"host\":\"fp.jdpay.com\",\"basePath\":\"/api\",\"tags\":[{\"name\":\"commodity111\",\"description\":\"商品管理111\"},{\"name\":\"pet111\",\"description\":\"pet controller222\"}],\"schemes\":[\"http\"],\"paths\":{\"/getPetById\":{\"get\":{\"tags\":[\"pet111\"],\"summary\":\"查询 pet by ID\",\"description\":\"Returns a pet when ID < 10. ID > 10 or nonintegers will simulate API error conditions\",\"operationId\":\"getPetById333\",\"parameters\":[{\"in\":\"body\",\"name\":\"body\",\"description\":\"ID of pet that needs to be fetched\",\"required\":true,\"schema\":{\"type\":\"integer\",\"format\":\"int64\"}}],\"responses\":{\"200\":{\"description\":\"successful operation\",\"schema\":{\"$ref\":\"#/definitions/Pet\"}},\"400\":{\"description\":\"Invalid ID supplied\"},\"404\":{\"description\":\"Pet not found\"}},\"security\":[{\"api_key\":[]}]}},\"/list\":{\"post\":{\"tags\":[\"commodity111\"],\"summary\":\"获得商品信息\",\"description\":\"获取商品信息(用于数据同步)\",\"operationId\":\"list\",\"parameters\":[{\"in\":\"body\",\"name\":\"body\",\"description\":\"Json参数\",\"required\":true,\"schema\":{\"$ref\":\"#/definitions/BaseParam\"}}],\"responses\":{\"200\":{\"description\":\"商品信息\",\"schema\":{\"$ref\":\"#/definitions/ResultDTO\"}},\"201\":{\"description\":\"(token验证失败)\"},\"202\":{\"description\":\"(系统错误)\"}}}}},\"definitions\":{\"BaseParam\":{\"type\":\"object\",\"required\":[\"shopId222\"],\"properties\":{\"shopId222\":{\"type\":\"integer\",\"format\":\"int64\",\"description\":\"商店Id\"}}},\"Category\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"format\":\"int64\"},\"name444\":{\"type\":\"string\"}},\"xml\":{\"name\":\"Category\"}},\"Pet\":{\"type\":\"object\",\"required\":[\"name\",\"photoUrls\"],\"properties\":{\"id\":{\"type\":\"integer\",\"format\":\"int64\"},\"category\":{\"$ref\":\"#/definitions/Category\"},\"name\":{\"type\":\"string\",\"example\":\"doggie\"},\"photoUrls\":{\"type\":\"array\",\"xml\":{\"name\":\"photoUrl\",\"wrapped\":true},\"items\":{\"type\":\"string\"}},\"tags\":{\"type\":\"array\",\"xml\":{\"name\":\"tag\",\"wrapped\":true},\"items\":{\"$ref\":\"#/definitions/Tag\"}},\"status\":{\"type\":\"string\",\"description\":\"pet status in the store\",\"enum\":[\"available\",\"pending\",\"sold\"]}},\"xml\":{\"name\":\"Pet\"}},\"ResultDTO\":{\"type\":\"object\",\"required\":[\"data\",\"message\",\"status\"],\"properties\":{\"status\":{\"type\":\"integer\",\"format\":\"int32\",\"description\":\"状态 (0:失败 1:成功)\"},\"message\":{\"type\":\"string\",\"description\":\"错误消息\"},\"data\":{\"type\":\"object\",\"description\":\"返回数据\"}}},\"Tag\":{\"type\":\"object\"}}}";
		System.out.println(swaggerJson);
		Map<String, String> params = new HashMap<String, String>();
		params.put("params", swaggerJson);
		params.put("jediProjectName", "test3");
		String url = "http://127.0.0.1:3000/api/interface/saveByMaven";
		HttpclientUtil.executeHttpPost(url, params);
	}
}
