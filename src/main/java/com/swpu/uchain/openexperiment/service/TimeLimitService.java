package com.swpu.uchain.openexperiment.service;

import com.swpu.uchain.openexperiment.form.time.TimeLimitForm;
import com.swpu.uchain.openexperiment.result.Result;

public interface TimeLimitService {

    Result insert(TimeLimitForm form);

    Result update(TimeLimitForm form);

    Result delete(Integer type);

    Result getTimeLimitByType(Integer type);

}
