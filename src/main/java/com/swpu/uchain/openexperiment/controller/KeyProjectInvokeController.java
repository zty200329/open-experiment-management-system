package com.swpu.uchain.openexperiment.controller;

import com.swpu.uchain.openexperiment.form.check.KeyProjectCheck;
import com.swpu.uchain.openexperiment.form.project.KeyProjectApplyForm;
import com.swpu.uchain.openexperiment.result.Result;
import com.swpu.uchain.openexperiment.service.KeyProjectService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * @author dengg
 */
@CrossOrigin
@RestController
@RequestMapping("/project")
@Api(tags = "项目（重点）模块执行接口")
public class KeyProjectInvokeController {

    private KeyProjectService keyProjectService;


    @Autowired
    public KeyProjectInvokeController(KeyProjectService keyProjectService) {
        this.keyProjectService = keyProjectService;
    }

    @ApiOperation("重点项目申请接口")
    @PostMapping(value = "/createKeyApply")
    public Result createKeyApply(@Valid @RequestBody KeyProjectApplyForm form){
        return keyProjectService.createKeyApply(form);
    }

    @ApiOperation("指导老师同意带审核项目")
    @PostMapping(value = "/agreeKeyProjectByGuideTeacher")
    public Result agreeKeyProjectByGuideTeacher(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.agreeKeyProjectByGuideTeacher(list);
    }

    @ApiOperation("实验室主任同意带审核项目")
    @PostMapping(value = "/agreeKeyProjectByLabAdministrator")
    public Result agreeKeyProjectByLabAdministrator(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.agreeKeyProjectByLabAdministrator(list);
    }

    @ApiOperation("二级单位主任同意带审核项目")
    @PostMapping(value = "/agreeKeyProjectBySecondaryUnit")
    public Result agreeKeyProjectBySecondaryUnit(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.agreeKeyProjectBySecondaryUnit(list);
    }

    @ApiOperation("职能部门同意带审核项目")
    @PostMapping(value = "/agreeKeyProjectByFunctionalDepartment")
    public Result agreeKeyProjectByFunctionalDepartment(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.agreeKeyProjectByFunctionalDepartment(list);
    }

    @ApiOperation("实验室主任上报已审核项目")
    @PostMapping(value = "/reportKeyProjectByLabAdministrator")
    public Result reportKeyProjectByLabAdministrator(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.reportKeyProjectByLabAdministrator(list);
    }

    @ApiOperation("二级单位主任上报已审核项目")
    @PostMapping(value = "/reportKeyProjectBySecondaryUnit")
    public Result reportKeyProjectBySecondaryUnit(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.reportKeyProjectBySecondaryUnit(list);
    }

    @ApiOperation("指导老师拒绝带审核项目")
    @PostMapping(value = "/rejectKeyProjectByGuideTeacher")
    public Result rejectKeyProjectByGuideTeacher(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.rejectKeyProjectByGuideTeacher(list);
    }

    @ApiOperation("实验室主任拒绝带审核项目")
    @PostMapping(value = "/rejectKeyProjectByLabAdministrator")
    public Result rejectKeyProjectByLabAdministrator(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.rejectKeyProjectByLabAdministrator(list);
    }

    @ApiOperation("二级单位主任拒绝带审核项目")
    @PostMapping(value = "/rejectKeyProjectBySecondaryUnit")
    public Result rejectKeyProjectBySecondaryUnit(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.rejectKeyProjectBySecondaryUnit(list);
    }

    @ApiOperation("职能部门拒绝带审核项目")
    @PostMapping(value = "/rejectKeyProjectByFunctionalDepartment")
    public Result rejectKeyProjectByFunctionalDepartment(@Valid @RequestBody List<KeyProjectCheck> list){
        return keyProjectService.rejectKeyProjectByFunctionalDepartment(list);
    }

}