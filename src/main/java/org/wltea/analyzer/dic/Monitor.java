package org.wltea.analyzer.dic;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.wltea.analyzer.help.ESPluginLoggerFactory;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class Monitor implements Runnable {

	private static final Logger logger = ESPluginLoggerFactory.getLogger(Monitor.class.getName());

	private static final CloseableHttpClient httpclient = HttpClients.createDefault();
	/*
	 * 上次更改时间
	 */
	private String last_modified;
	/*
	 * 资源属性
	 */
	private String eTags;

	/*
	 * 请求地址
	 */
	private final String location;

	/**
	 * 初始化构造一个 Monitor
	 * @param location 需要监控的 Url
	 */
	public Monitor(String location) {
		this.location = location;
		this.last_modified = null;
		this.eTags = null;
	}

	public void run() {
		// 这里时 java.security 安全管理器中的操作，
		SpecialPermission.check();
		AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
			this.runUnprivileged();
			return null;
		});
	}

	/**
	 * 监控流程：
	 *  ①向词库服务器发送Head请求
	 *  ②从响应中获取Last-Modify、ETags字段值，判断是否变化
	 *  ③如果未变化，休眠1min，返回第①步
	 * 	④如果有变化，重新加载词典
	 *  ⑤休眠1min，返回第①步
	 */

	public void runUnprivileged() {

		//超时设置
		RequestConfig rc = RequestConfig.custom().setConnectionRequestTimeout(10*1000)
				.setConnectTimeout(10*1000).setSocketTimeout(15*1000).build();

		HttpHead head = new HttpHead(location);
		head.setConfig(rc);

		//设置请求头，不为空说明不是第一次请求，初次运行时，last_modified与eTags都应该为空
		if (last_modified != null) {
			head.setHeader("If-Modified-Since", last_modified);
		}
		if (eTags != null) {
			head.setHeader("If-None-Match", eTags);
		}

		CloseableHttpResponse response = null;
		try {

			response = httpclient.execute(head);

			//返回200 才做操作
			if(response.getStatusLine().getStatusCode()==200){
				// last-modified 或者 etags不为空，且与上次记录的内容不同，表示发生了修改
				if (((response.getLastHeader("Last-Modified")!=null) && !response.getLastHeader("Last-Modified").getValue().equalsIgnoreCase(last_modified))
						||((response.getLastHeader("ETag")!=null) && !response.getLastHeader("ETag").getValue().equalsIgnoreCase(eTags))) {

					// 远程词库有更新,需要重新加载词典，并修改last_modified,eTags
					Dictionary.getSingleton().reLoadMainDict();
					last_modified = response.getLastHeader("Last-Modified")==null?null:response.getLastHeader("Last-Modified").getValue();
					eTags = response.getLastHeader("ETag")==null?null:response.getLastHeader("ETag").getValue();
				}
			}else if (response.getStatusLine().getStatusCode()==304) {
				//没有修改，不做操作
				//noop
			}else{
				logger.info("remote_ext_dict {} return bad code {}" , location , response.getStatusLine().getStatusCode() );
			}

		} catch (Exception e) {
			logger.error("remote_ext_dict {} error!",e , location);
		}finally{
			try {
				if (response != null) {
					response.close();
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

}
