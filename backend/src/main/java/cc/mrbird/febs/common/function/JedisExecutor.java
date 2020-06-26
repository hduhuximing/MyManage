package cc.mrbird.febs.common.function;

import cc.mrbird.febs.common.exception.RedisConnectException;
//函数式编程
@FunctionalInterface
public interface JedisExecutor<T, R> {
    R excute(T t) throws RedisConnectException;
}
