package com.aip.app.service;

import com.aip.app.dto.StatQueryDTO;
import com.aip.app.entity.InvocationStat;
import com.aip.app.mapper.InvocationStatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 调用统计服务实现
 */
@Slf4j
@Service
public class InvocationStatServiceImpl implements IInvocationStatService {

    @Autowired
    private InvocationStatMapper statMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    @Transactional
    public void recordInvocation(String statDate, String assistantId, String assistantCode,
                                 String userId, Long invokeCount, Long successCount,
                                 Long failCount, Long tokens, Long responseTime) {
        try {
            InvocationStat stat = statMapper.findByStatDateAndAssistantId(statDate, assistantId)
                    .orElseGet(() -> {
                        InvocationStat newStat = new InvocationStat();
                        newStat.setStatDate(statDate);
                        newStat.setAssistantId(assistantId);
                        newStat.setAssistantCode(assistantCode);
                        newStat.setUserId(userId);
                        newStat.setInvokeCount(0L);
                        newStat.setSuccessCount(0L);
                        newStat.setFailCount(0L);
                        newStat.setTotalTokens(0L);
                        newStat.setTotalResponseTime(0L);
                        newStat.setAvgResponseTime(0L);
                        return newStat;
                    });

            stat.setInvokeCount(stat.getInvokeCount() + invokeCount);
            stat.setSuccessCount(stat.getSuccessCount() + successCount);
            stat.setFailCount(stat.getFailCount() + failCount);
            stat.setTotalTokens(stat.getTotalTokens() + tokens);
            stat.setTotalResponseTime(stat.getTotalResponseTime() + responseTime);
            if (stat.getInvokeCount() > 0) {
                stat.setAvgResponseTime(stat.getTotalResponseTime() / stat.getInvokeCount());
            }

            statMapper.save(stat);
            log.debug("记录调用统计成功: date={}, assistant={}, invokeCount={}",
                    statDate, assistantCode, invokeCount);
        } catch (Exception e) {
            log.error("记录调用统计失败: date={}, assistant={}", statDate, assistantCode, e);
        }
    }

    @Override
    public InvocationStat getTodayStat(String assistantId) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return statMapper.findByStatDateAndAssistantId(today, assistantId).orElse(null);
    }

    @Override
    public InvocationStat getStatByDate(String statDate, String assistantId) {
        return statMapper.findByStatDateAndAssistantId(statDate, assistantId).orElse(null);
    }

    @Override
    public List<InvocationStat> getStatsByDateRange(String startDate, String endDate) {
        return statMapper.findByStatDateBetween(startDate, endDate);
    }

    @Override
    public List<InvocationStat> getStatsByAssistant(String assistantId) {
        return statMapper.findByAssistantId(assistantId);
    }

    @Override
    public List<InvocationStat> getStatsByUser(String userId) {
        return statMapper.findByUserId(userId);
    }

    @Override
    public List<InvocationStat> queryStats(StatQueryDTO query) {
        if (query.getStartDate() != null && query.getEndDate() != null) {
            return statMapper.findByStatDateBetween(query.getStartDate(), query.getEndDate());
        }
        if (query.getAssistantId() != null) {
            return statMapper.findByAssistantId(query.getAssistantId());
        }
        if (query.getUserId() != null) {
            return statMapper.findByUserId(query.getUserId());
        }
        if (query.getStatDate() != null) {
            String date = query.getStatDate();
            return statMapper.findByStatDateBetween(date, date);
        }
        return statMapper.findAll();
    }

    @Override
    public Long getTodayTotalInvokeCount() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        Long count = statMapper.sumInvokeCountByDate(today);
        return count != null ? count : 0L;
    }

    @Override
    public Long getAssistantTotalInvokeCount(String assistantId) {
        Long count = statMapper.sumInvokeCountByAssistantId(assistantId);
        return count != null ? count : 0L;
    }

    @Override
    public Long getAssistantTodayInvokeCount(String assistantId) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        Long count = statMapper.sumInvokeCountByDateAndAssistantId(today, assistantId);
        return count != null ? count : 0L;
    }
}
