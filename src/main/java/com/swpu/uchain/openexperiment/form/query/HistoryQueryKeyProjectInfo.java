package com.swpu.uchain.openexperiment.form.query;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class HistoryQueryKeyProjectInfo {

    /**
     * {@link com.swpu.uchain.openexperiment.enums.RoleType}
     */
    @NotNull
    @Min(4)
    @Max(6)
    @ApiModelProperty("操作单位  4,实验室主任；5,二级单位(学院领导)；6,职能部门")
    private Integer operationUnit;

    /**
     * {@link com.swpu.uchain.openexperiment.enums.OperationType}
     */
    @Min(12)
    @Max(13)
    @ApiModelProperty("历史操作： 12,拒绝|13,上报")
    private Integer operationType;

}
