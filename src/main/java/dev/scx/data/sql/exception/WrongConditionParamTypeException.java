package dev.scx.data.sql.exception;

import dev.scx.data.query.ConditionType;

/// Condition 参数类型异常
///
/// @author scx567888
public final class WrongConditionParamTypeException extends IllegalArgumentException {

    public WrongConditionParamTypeException(String selector, ConditionType conditionType, String paramType) {
        super("Condition 参数类型错误 : selector : " + selector + " , conditionType : " + conditionType + " , 参数类型无法转换为 " + paramType + " !!!");
    }

}
