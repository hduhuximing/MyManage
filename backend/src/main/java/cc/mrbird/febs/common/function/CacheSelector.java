package cc.mrbird.febs.common.function;
//基于java8 实现函数式
@FunctionalInterface
public interface CacheSelector<T> {
    T select() throws Exception;
}
