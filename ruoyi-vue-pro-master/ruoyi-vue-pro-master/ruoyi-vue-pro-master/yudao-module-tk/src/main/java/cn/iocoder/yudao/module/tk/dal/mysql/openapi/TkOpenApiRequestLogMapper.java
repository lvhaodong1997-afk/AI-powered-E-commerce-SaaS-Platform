package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiRequestLogDO;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiGovernanceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TkOpenApiRequestLogMapper extends BaseMapperX<TkOpenApiRequestLogDO> {
    default long selectCountByClientAndDate(String clientId, LocalDate date) {
        return selectCount(new LambdaQueryWrapperX<TkOpenApiRequestLogDO>()
                .eq(TkOpenApiRequestLogDO::getClientId, clientId)
                .eq(TkOpenApiRequestLogDO::getRequestDate, date));
    }

    @Select("<script>"
            + "SELECT request_date AS requestDate, client_id AS clientId, COUNT(*) AS requestCount, "
            + "SUM(CASE WHEN http_status &gt;= 200 AND http_status &lt; 400 THEN 1 ELSE 0 END) AS successCount, "
            + "SUM(CASE WHEN http_status &gt;= 400 THEN 1 ELSE 0 END) AS failureCount, "
            + "CAST(AVG(duration_ms) AS SIGNED) AS averageDurationMs "
            + "FROM tk_open_api_request_log WHERE deleted = 0 "
            + "<if test='clientId != null and clientId != \"\"'>AND client_id = #{clientId} </if>"
            + "AND request_date &gt;= #{startDate} AND request_date &lt;= #{endDate} "
            + "GROUP BY request_date, client_id ORDER BY request_date DESC, client_id ASC"
            + "</script>")
    List<TkOpenApiGovernanceVO.UsageResp> selectDailyUsage(@Param("clientId") String clientId,
                                                            @Param("startDate") LocalDate startDate,
                                                            @Param("endDate") LocalDate endDate);
}
