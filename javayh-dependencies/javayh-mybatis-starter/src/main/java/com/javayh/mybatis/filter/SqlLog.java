/**
 *   Copyright [2020] [Yang Hai Ji of copyright owner]
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.javayh.mybatis.filter;

import com.javayh.common.exception.BaseException;
import com.javayh.common.util.MapUtils;
import com.javayh.mybatis.uitl.Constant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.util.CollectionUtils;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * <p>
 * sql记录器
 * </p>
 *
 * @author Dylan-haiji
 * @version 1.0.0
 * @since 2020-03-31 17:01
 */
@Slf4j
public class SqlLog {

    private static final String PREFIX = "Java有货==>";
    private static final String NEWLINE = "\r\n";

    private static final int MAX_STACK_DEPTH = 10;

	/**
	 * <p>
	 * 封装了一下sql语句，使得结果返回完整xml路径下的sql语句节点id + sql语句
	 * </p>
	 * @version 1.0.0
	 * @author Dylan-haiji
	 * @since 2020/3/30
	 * @param configuration
	 * @param boundSql
	 * @param sqlId
	 * @return java.lang.String
	 */
    public static String getSql(Configuration configuration, BoundSql boundSql, String sqlId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        String sql = showSql(configuration, boundSql);
        sb.append(NEWLINE).append(PREFIX).append("执行的SQL_ID为==>: ").append(sqlId);
        sb.append(NEWLINE).append(PREFIX).append("执行的SQL为==>: ").append(sql);
        log.info(sb.toString());
        // 获取参数
        MapperMethod.ParamMap parameterObject= new MapperMethod.ParamMap();
        MapperMethod.ParamMap parameterObjectP = (MapperMethod.ParamMap) boundSql.getParameterObject();
        int size =  parameterObjectP.size();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if(size >> 2 == parameterMappings.size()){
            return sql;
        }
        for (int i = 1; i <= size >> 2; i++) {
            parameterObject.put("param"+i,parameterObjectP.get("param"+i));
        }
        for (ParameterMapping mapping: parameterMappings) {
            parameterObject.put(mapping.getProperty(),parameterObjectP.get(mapping.getProperty()));
        }
        Map<String, Object> differenceSetByGuava = MapUtils.getDifferenceSetByGuava(parameterObjectP, parameterObject);
        Boolean discover = sqlDiscover(differenceSetByGuava);
        if(!discover){
            throw new BaseException("SQL 存在异常,请检查您的SQL是否规范");
        }else {
            return sql;
        }
    }

	/**
	 * <p>
	 * 如果参数是String，则添加单引号， 如果是日期，则转换为时间格式器并加单引号； 对参数是null和不是null的情况作了处理
	 * </p>
	 * @version 1.0.0
	 * @author Dylan-haiji
	 * @since 2020/3/30
	 * @param obj
	 * @return java.lang.String
	 */
	private static String getParameterValue(Object obj) {
		String value = null;
		if (obj instanceof String) {
			value = "'" + obj.toString() + "'";
		}
		else if (obj instanceof Date) {
			DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT,
					DateFormat.DEFAULT, Locale.CHINA);
			value = "'" + formatter.format(new Date()) + "'";
		}
		else {
			if (obj != null) {
				value = obj.toString();
			}
			else {
				value = "";
			}
		}
		return value;
	}

	/**
	 * <p>
	 * 进行？的替换
	 * </p>
	 * @version 1.0.0
	 * @author Dylan-haiji
	 * @since 2020/3/31
	 * @param configuration
	 * @param boundSql
	 * @return java.lang.String
	 */
	public static String showSql(Configuration configuration, BoundSql boundSql) throws SQLException {
		// 获取参数
		Object parameterObject = boundSql.getParameterObject();
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		// sql语句中多个空格都用一个空格代替
		String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
		if (!CollectionUtils.isEmpty(parameterMappings) && parameterObject != null) {
			// 获取类型处理器注册器，类型处理器的功能是进行java类型和数据库类型的转换
			TypeHandlerRegistry typeHandlerRegistry = configuration
					.getTypeHandlerRegistry();
			// 如果根据parameterObject.getClass(）可以找到对应的类型，则替换
			if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
				sql = sql.replaceFirst("\\?",
						Matcher.quoteReplacement(getParameterValue(parameterObject)));
			}
			else {
				// MetaObject主要是封装了originalObject对象，
				// 提供了get和set的方法用于获取和设置originalObject的属性值,
				// 主要支持对JavaBean、Collection、Map三种类型对象的操作
				MetaObject metaObject = configuration.newMetaObject(parameterObject);
				for (ParameterMapping parameterMapping : parameterMappings) {
					String propertyName = parameterMapping.getProperty();
					if (metaObject.hasGetter(propertyName)) {
						Object obj = metaObject.getValue(propertyName);
                        Boolean discover = sqlDiscover(obj);
                        if(!discover){
                            throw new SQLException("SQL 存在异常,请检查您的SQL是否规范");
                        }
						sql = sql.replaceFirst("\\?",
								Matcher.quoteReplacement(getParameterValue(obj)));

					}
					else if (boundSql.hasAdditionalParameter(propertyName)) {
						// 该分支是动态sql
						Object obj = boundSql.getAdditionalParameter(propertyName);
                        Boolean discover = sqlDiscover(obj);
                        if(!discover){
                            throw new SQLException("SQL 存在异常,请检查您的SQL是否规范");
                        }
						sql = sql.replaceFirst("\\?",
								Matcher.quoteReplacement(getParameterValue(obj)));
					}
					else {
						// 打印出缺失，提醒该参数缺失并防止错位
						sql = sql.replaceFirst("\\?", "缺失");
					}
				}
			}
		}
		return sql;
	}

    private static Boolean sqlDiscover(Object obj){
        boolean b = ObjectUtils.allNotNull(obj);
        StringBuilder sb = new StringBuilder();
        sb.append(NEWLINE).append(PREFIX).append("参数的值为==>: ").append(obj).append(",是否为空值==>: ").append(b);
        log.info(sb.toString());
        if(b){
            String input = obj.toString();
            if (input.contains(Constant.DELETE) || input.contains(Constant.ASCII)
                    || input.contains(Constant.UPDATE) || input.contains(Constant.SELECT)
                    || input.contains(Constant.S) || input.contains(Constant.SUBSTR)
                    || input.contains(Constant.COUNT) || input.contains(Constant.OR)
                    || input.contains(Constant.AND) || input.contains(Constant.DROP)
                    || input.contains(Constant.EXECUTE) || input.contains(Constant.EXEC)
                    || input.contains(Constant.TRUNCATE) || input.contains(Constant.INTO)
                    || input.contains(Constant.DECLARE) || input.contains(Constant.MASTER)) {
                sb.append(NEWLINE).append(PREFIX).append("==============================Java有货==============================");
                sb.append(NEWLINE).append(PREFIX).append("===========该参数存在SQL注入风险：sInput=").append(input).append("===========");
                sb.append(NEWLINE).append(PREFIX).append("==============================Java有货==============================");
                log.error(sb.toString());
                return false;
            }else {
                sb.append(NEWLINE).append(PREFIX).append("==============================Java有货==============================");
                sb.append(NEWLINE).append(PREFIX).append("=======================通过SQL检测,未发现注入风险======================");
                sb.append(NEWLINE).append(PREFIX).append("==============================Java有货==============================");
                log.info(sb.toString());
                return true;
            }
        }else {
            return true;
        }
    }
}
