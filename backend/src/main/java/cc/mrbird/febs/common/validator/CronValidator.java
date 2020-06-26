package cc.mrbird.febs.common.validator;

import cc.mrbird.febs.common.annotation.IsCron;
import org.quartz.CronExpression;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * 校验是否为合法的 Cron表达式
 */
public class CronValidator implements ConstraintValidator<IsCron, String> {

    @Override
    public void initialize(IsCron isCron) {
        System.out.println("Cron验证");
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        try {
            //quartz包内的验证Cron是否符合Cron表达式
            return CronExpression.isValidExpression(value);
        } catch (Exception e) {
            return false;
        }
    }
}