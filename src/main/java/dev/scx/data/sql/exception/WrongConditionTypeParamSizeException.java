package dev.scx.data.sql.exception;

import dev.scx.data.query.ConditionType;

/// Condition 参数长度异常
///
/// @author scx567888
public final class WrongConditionTypeParamSizeException extends IllegalArgumentException {

    public WrongConditionTypeParamSizeException(String selector, ConditionType conditionType, int paramSize) {
        super("Condition 参数长度错误 : selector : " + selector + " , conditionType : " + conditionType + " , 有效 (不为 null) 的参数数量必须为 " + paramSize + " 个 !!!");
    }

}
