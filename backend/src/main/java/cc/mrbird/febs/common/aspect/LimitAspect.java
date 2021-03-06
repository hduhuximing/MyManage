package cc.mrbird.febs.common.aspect;

import cc.mrbird.febs.common.annotation.Limit;
import cc.mrbird.febs.common.domain.LimitType;
import cc.mrbird.febs.common.exception.LimitAccessException;
import cc.mrbird.febs.common.utils.IPUtil;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Objects;


/**
 * 接口限流
 */
@Slf4j
@Aspect
@Component
public class LimitAspect {

    private final RedisTemplate<String, Serializable> limitRedisTemplate;

    @Autowired
    public LimitAspect(RedisTemplate<String, Serializable> limitRedisTemplate) {
        this.limitRedisTemplate = limitRedisTemplate;
    }

    @Pointcut("@annotation(cc.mrbird.febs.common.annotation.Limit)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        HttpServletRequest request =
                ((ServletRequestAttributes) Objects
                        .requireNonNull(RequestContextHolder.getRequestAttributes()))
                        .getRequest();
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Limit limitAnnotation = method.getAnnotation(Limit.class);
        LimitType limitType = limitAnnotation.limitType();
        String name = limitAnnotation.name();
        String key;
        String ip = IPUtil.getIpAddr(request);
        int limitPeriod = limitAnnotation.period();
        int limitCount = limitAnnotation.count();
        switch (limitType) {
            case IP:
                key = ip;
                break;
            case CUSTOMER:
                key = limitAnnotation.key();
                break;
            default:
                key = StringUtils.upperCase(method.getName());
        }
        ImmutableList<String> keys =
                ImmutableList.of(StringUtils.join(limitAnnotation.prefix() + "_", key, ip));
        String luaScript = buildLuaScript();
        RedisScript<Number> redisScript = new DefaultRedisScript<>(luaScript, Number.class);
        Number count = limitRedisTemplate.execute(redisScript, keys, limitCount, limitPeriod);
        log.info("IP:{} 第 {} 次访问key为 {}，描述为 [{}] 的接口", ip, count, keys, name);
        if (count != null && count.intValue() <= limitCount) {
            return point.proceed();
        } else {
            throw new LimitAccessException("接口访问超出频率限制");
        }
    }

    /**
     * 当前基于简单时间过期进行判断，后续可以改为令牌桶模式，通过时间差进行判断是否需要增加令牌
     *
     * 限流脚本
     * 调用的时候不超过阈值，则直接返回并执行计算器自加。
     * 先看一下limit的lua脚本，需要给脚本传两个值，
     * 一个值是限流的key,一个值是限流的数量。获取当前key，然后判断其值是否为nil，
     * 如果为nil的话需要赋值为0，然后进行加1并且和limit进行比对，
     * 如果大于limt即返回，说明限流了，如果小于limit则需要使用Redis的INCRBY key 1,就是将key进行加1命令。
     * 并且设置超时时间，超时时间是秒，并且如果有需要的话这个秒也是可以用参数进行设置。     * @return lua脚本
     */
    private String buildLuaScript() {
        return "local c" +
                "\nc = redis.call('get',KEYS[1])" +
                "\nif c and tonumber(c) > tonumber(ARGV[1]) then" +
                "\nreturn c;" +
                "\nend" +
                "\nc = redis.call('incr',KEYS[1])" +
                "\nif tonumber(c) == 1 then" +
                "\nredis.call('expire',KEYS[1],ARGV[2])" +
                "\nend" +
                "\nreturn c;";
    }


    /**
     * 漏桶
     * @return
     */
    private String funnelRateStr() {
        StringBuilder builder = new StringBuilder();
        builder.append("local limitInfo = redis.call('hmget', KEYS[1], 'capacity', 'funnelRate', 'requestNeed', 'water', 'lastTs')\n")
                .append("local capacity = limitInfo[1]\n").append("local funnelRate = limitInfo[2]\n")
                .append("local requestNeed = limitInfo[3]\n").append("local water = limitInfo[4]\n")
                .append("local lastTs = limitInfo[5]\n").append("if capacity == false then\n")
                .append("    capacity = tonumber(ARGV[1])\n").append("    funnelRate = tonumber(ARGV[2])\n")
                .append("    requestNeed = tonumber(ARGV[3])\n").append("    water = 0\n")
                .append("    lastTs = tonumber(ARGV[4])\n").append("    redis.call('hmset', KEYS[1], 'capacity', capacity, 'funnelRate', funnelRate, 'requestNeed', requestNeed, 'water', water, 'lastTs', lastTs)\n")
                .append("    return true\n").append("else\n").append("    local nowTs = tonumber(ARGV[4])\n")
                .append("    local waterPass = tonumber((nowTs - lastTs) * funnelRate)\n").append("    water = math.max(0, water - waterPass)\n")
                .append("    lastTs = nowTs\n").append("    requestNeed = tonumber(requestNeed)\n").append("    if capacity - water >= requestNeed then\n")
                .append("        water = water + requestNeed\n").append("        redis.call('hmset', KEYS[1], 'water', water, 'lastTs', lastTs)\n")
                .append("        return true\n    end\n    return false\nend");
        return builder.toString();
    }

    /**
     * 令牌桶
     * @return
     */
    private String tokenRateStr() {
        StringBuilder builder = new StringBuilder();
        builder.append("local limitInfo = redis.call('hmget', KEYS[1], 'capacity', 'funnelRate', 'leftToken', 'lastTs')\n")
                .append("local capacity = limitInfo[1]\n").append("local tokenRate = limitInfo[2]\n")
                .append("local leftToken = limitInfo[3]\n").append("local lastTs = limitInfo[4]\n")
                .append("if capacity == false then\n").append("    capacity = tonumber(ARGV[1])\n")
                .append("    tokenRate = tonumber(ARGV[2])\n").append("    leftToken = tonumber(ARGV[5])\n")
                .append("    lastTs = tonumber(ARGV[4])\n").append("    redis.call('hmset', KEYS[1], 'capacity', capacity, 'funnelRate', tokenRate, 'leftToken', leftToken, 'lastTs', lastTs)\n")
                .append("    return -1\nelse\n").append("    local nowTs = tonumber(ARGV[4])\n")
                .append("    local genTokenNum = tonumber((nowTs - lastTs) * tokenRate)\n").append("    leftToken = genTokenNum + leftToken\n")
                .append("    leftToken = math.min(capacity, leftToken)\n    lastTs = nowTs\n    local requestNeed = tonumber(ARGV[3])\n")
                .append("    if leftToken >= requestNeed then\n        leftToken = leftToken - requestNeed\n")
                .append("        redis.call('hmset', KEYS[1], 'leftToken', leftToken, 'lastTs', lastTs)\n")
                .append("        return -1\n    end\n    return (requestNeed - leftToken) / tokenRate\nend");
        return builder.toString();
    }

}
