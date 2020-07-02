package cn.dblearn.blog.config;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.support.http.StatViewServlet;
import com.google.common.collect.Lists;
import io.shardingjdbc.core.jdbc.core.datasource.MasterSlaveDataSource;
import io.shardingjdbc.core.yaml.masterslave.YamlMasterSlaveConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Collections;
import java.util.StringTokenizer;

/**
 * 读写分离数据源配置
 */
@Configuration
public class ShardingDataSourceConfig implements ApplicationContextAware {
	@Autowired
	private Filter statFilter;

	@Autowired
	private Filter wallFilter;

	private ApplicationContext context;

	private static final String SHARDING_YML_PATH = "sharding/dataSource";

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.context = applicationContext;
	}

	/**
	 * 获取当前profile
	 * @return
	 */
	public String getActiveProfile() {
		String[] profiles = context.getEnvironment().getActiveProfiles();
		if (!ArrayUtils.isEmpty(profiles)) {
			return profiles[0];
		}
		return "";
	}

	@Bean
	public DataSource dataSource() throws SQLException, IOException {
		YamlMasterSlaveConfiguration config = parse();
		config.getDataSources().forEach((k, v) -> {
			DruidDataSource d = (DruidDataSource) v;
			d.setProxyFilters(Lists.newArrayList(statFilter, wallFilter));
			//合并多个DruidDataSource的监控数据
			d.setUseGlobalDataSourceStat(true);
			//允许数据库保存emoji表情
			StringTokenizer tokenizer = new StringTokenizer("SET NAMES utf8mb4", ";");
			d.setConnectionInitSqls(Collections.list(tokenizer));
		});
		return new MasterSlaveDataSource(config.getMasterSlaveRule(config.getDataSources()),
				config.getMasterSlaveRule().getConfigMap());
	}

	private YamlMasterSlaveConfiguration parse() throws IOException {
		String shardingYmlFile = SHARDING_YML_PATH + "-" + getActiveProfile() + ".yml";
		Resource certResource = new ClassPathResource(shardingYmlFile);
		try (InputStreamReader inputStreamReader = new InputStreamReader(certResource.getInputStream(), "UTF-8")) {
			return new Yaml(new Constructor(YamlMasterSlaveConfiguration.class)).loadAs(inputStreamReader,
					YamlMasterSlaveConfiguration.class);
		}
	}

	@Bean
	public ServletRegistrationBean druidServlet() {
		ServletRegistrationBean bean = new ServletRegistrationBean(new StatViewServlet(), "/public/druid/*");
		//bean.addInitParameter("allow", "");
		//servletRegistrationBean.addInitParameter("deny", this.deny);
		bean.addInitParameter("loginUsername", "admin");
		bean.addInitParameter("loginPassword", "admin");
		return bean;
	}
}
