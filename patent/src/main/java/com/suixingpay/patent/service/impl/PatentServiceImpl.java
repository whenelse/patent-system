package com.suixingpay.patent.service.impl;

import com.suixingpay.patent.mapper.IndexMapper;
import com.suixingpay.patent.mapper.PatentMapper;
import com.suixingpay.patent.pojo.Index;
import com.suixingpay.patent.pojo.Message;
import com.suixingpay.patent.pojo.Patent;
import com.suixingpay.patent.service.PatentService;
import com.suixingpay.patent.util.DateSetting;
import com.suixingpay.patent.util.ParamCheck;
import org.jxls.util.JxlsHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.servlet.http.HttpServletResponse;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

@Service
public class PatentServiceImpl implements PatentService {

    @Autowired
    private PatentMapper patentMapper;

    @Autowired
    private IndexMapper indexMapper;

    @Autowired
    private Message message;

    @Value("${patentNeedCheckStatus}")
    private Integer patentStatusId;

    /**
     * 插入专利信息
     * @param patent
     * @return
     */
    @Transactional
    @Override
    public Message insertPatentService(Patent patent, String[] indexContent) {
        //统一前端时间的小时
        DateSetting.unifyDate(patent.getPatentApplyTime());
        //安全参数替换，将特殊字符替换为空格
        ParamCheck.patentParamReplace(patent);
        //设置专利新建的基本信息
        patent.setPatentSign(0); //设置审核状态为未审核
        patent.setPatentStatusId(0); //设置专利进度状态为新建专利
        patent.setPatentWriter(0);  //设置撰写人为待认领
        try {
            Integer patentId = patentMapper.insertPatent(patent); //返回插入的自增Id
            if (patentId == null) {
                message.setMessage(null, 200, "新建专利失败！", false);
                throw new RuntimeException("新建专利异常");
            }
            for(String str : indexContent) {
                Index index = new Index();
                index.setIndexPatentId(patentId);
                index.setIndexContent(str);
                index.setIndexCreateTime(new Date());
                if(indexMapper.insertIndexContent(index) == 0) {
                    message.setMessage(null, 200, "新建专利失败！", false);
                    throw new RuntimeException("新建专利插入指标异常");
                }
            }
            message.setMessage(null, 200, "新建专利成功！", true);
        }catch (Exception e) {
            //异常事务回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return message;
    }

    /**
     * 通过专利ID修改专利信息（编辑功能）
     * @param patent
     * @return
     */
    @Override
    public Message updatePatentServiceByIdService(Patent patent) {
        //统一前端时间的小时
        DateSetting.unifyDate(patent.getPatentApplyTime());
        //安全参数替换，将特殊字符替换为空格
        ParamCheck.patentParamReplace(patent);
        //设置条件不允许修改审核中的数据
        patent.setSpecialCondition("patent_sign != 1");
        if (patentMapper.updatePatent(patent) != 0) {
            message.setMessage(null, 200, "修改信息成功！", true);
            return message;
        }
        message.setMessage(null, 400, "非待审核状态，不允许修改！", false);
        return message;
    }

    /**
     * 通过专利Id修改撰写人信息（认领功能）
     * @param patent
     * @return
     */
    @Override
    public Message updatePatentWriterByIdService(Patent patent) {
        patent.setPatentStatusId(2); //认领成功将进度变为方案讨论
        if (patentMapper.updatePatent(patent) != 0) {
            message.setMessage(null, 200, "认领成功！", true);
            return message;
        }
        message.setMessage(null, 400, "条件不符，认领失败！", false);
        return message;
    }

    /**
     * 查询专利信息
     * 通过专利id查询、案件文号查询、申请号查询、申请日期查询、进度查询、发明人姓名查询、创建人id查询、撰写人id查询、其他条件
     * @param patent
     * @return
     */
    @Override
    public List<Patent> selectPatentService(Patent patent) {
        //统一前端时间的小时
        DateSetting.unifyDate(patent.getPatentApplyTime());
        //安全参数替换，将特殊字符替换为空格
        ParamCheck.patentParamReplace(patent);
        return patentMapper.selectPatent(patent);
    }

    /**
     * 指标维度查询
     * 通过指标内容、专利名称、案件文号查询、申请号查询、申请日期查询、进度查询、发明人姓名查询
     * @param patent
     * @return
     */
    @Override
    public List<Patent> selectPatentWithIndexService(Patent patent) {
        //统一前端时间的小时
        DateSetting.unifyDate(patent.getPatentApplyTime());
        //安全参数替换，将特殊字符替换为空格
        ParamCheck.patentParamReplace(patent);
        return patentMapper.selectPatentWithIndex(patent);
    }

    /**
     * 审核通过
     * @param patent
     * @return
     */
    @Transactional(rollbackFor = {RuntimeException.class, Error.class})
    @Override
    public Message auditPass(Patent patent) {
        Message message = new Message();
        List<Patent> list = patentMapper.selectPatent(patent);
        if (list.size() < 1) {
            message.setMessage(null, 400, "专利Id不存在！", false);
            return message;
        }
        /**
         * 审核通过
         * 若为审核阶段 将审核状态设为待审核状态0，并且将专利进度+1
         * 若下一阶段是数据维护阶段 ，将将审核状态设为数据维护状态3，并且将专利进度+1
         */
        if ((list.get(0).getPatentStatusId() + 1) > patentStatusId) {
            patent.setPatentSign(3);
        } else {
            patent.setPatentSign(0);
        }

        patent.setPatentSign(0);
        patent.setPatentStatusId(list.get(0).getPatentStatusId() + 1);
        patent.setSpecialCondition("patent_sign = 1 and patent_status_id <= " + patentStatusId);
        if (patentMapper.updatePatent(patent) != 0) {
            message.setMessage(null, 200, "审核通过成功！", true);
            return message;
        }
        message.setMessage(null, 400, "审核条件不符合！", false);
        return message;
    }

    /**
     * 驳回
     * @param patent
     * @return
     */
    @Transactional(rollbackFor = {RuntimeException.class, Error.class})
    @Override
    public Message auditFailed(Patent patent) {
        Message message = new Message();
        //审核不通过将审核状态设为审核不通过状态2
        patent.setPatentSign(2);
        //设置条件:审核进度为审核中2,专利进度为需要审核的阶段
        patent.setSpecialCondition("patent_sign = 1 and patent_status_id <= " + patentStatusId);
        if (patentMapper.updatePatent(patent) != 0) {
            message.setMessage(null, 200, "驳回成功！", true);
            return message;
        }
        message.setMessage(null, 400, "条件不符，驳回失败！", false);
        return message;
    }

    /**
     * 用户提交审核功能
     * @param patent
     * @return
     */
    @Override
    public Message userSubmitAudit(Patent patent) {
        Message message = new Message();
        //提交审核:将审核状态设为审核中1
        patent.setPatentSign(1);
        //设置条件:审核进度为审核中0,专利进度为需要审核的阶段
        patent.setSpecialCondition("patent_sign != 1 and patent_status_id <= " + patentStatusId);
        if (patentMapper.updatePatent(patent) != 0) {
            message.setMessage(null, 200, "提交审核成功！", true);
            return message;
        }
        message.setMessage(null, 200, "审核中，不要重复提交！", false);
        return message;
    }

    /**
     * 数据维护修改专利状态（初审状态及之后才能修改）
     * @param patent
     * @return
     */
    @Override
    public Message updateStatusId(Patent patent) {
        patent.setSpecialCondition("patent_status_id > " + patentStatusId); //设置数据维护阶段可以修改的进度
        if (patentMapper.updatePatent(patent) != 0) {
            message.setMessage(null, 200, "修改进度成功！", true);
            return message;
        }
        message.setMessage(null, 400, "条件不符，修改进度失败！", false);
        return message;
    }

    /**
     * 查询导出文件
     * @param response
     * @throws IOException
     */
    @Override
    public void exportDeviceModelMsg(HttpServletResponse response, String fileName, List<Patent> list)
            throws IOException {
        try {
            List<Patent> patentExcle = list; //获取列表数据
            InputStream in = this.getClass().getClassLoader().getResourceAsStream("excel/" + fileName + ".xls"); //文档路径
            //列表数据将存储到指定的excel文件路径，这个路径是在项目编译之后的target目录下
            String temp = System.getProperty("user.dir");
            String path = temp + "\\public\\" + fileName + ".xls";
            System.out.println("++++++++++++++++++++++++++++++++++++");
            System.out.println(path);
            FileOutputStream out = new FileOutputStream(path);
            //这里的context是jxls框架上的context内容
            org.jxls.common.Context context = new org.jxls.common.Context();
            //将列表参数放入context中
            context.putVar("patentExcle", patentExcle);
            //将List<Patent>列表数据按照模板文件中的格式生成到patent.xls文件中
            JxlsHelper.getInstance().processTemplate(in, out, context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
