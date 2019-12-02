package com.suixingpay.patent.mapper;

import com.suixingpay.patent.pojo.Status;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface StatusMapper {

    /**
     * 查询所有的进度状态
     * @return
     */
    List<Status> queryAllStatus();

    /**
     * 根据进度状态的id，查找对应状态的名称
     * @param statusId
     * @return
     */
    Status selectStatusNameById(Integer statusId);

    /**
     * 根据进度状态的名称，查找对应状态的id
     * @param statusName
     * @return
     */
    Status selectStatusIdByName(String statusName);

    /**
     * 查询出数据维护阶段的进度状态
     * @return
     */
    List<Status> queryAllStatusAfter();
}