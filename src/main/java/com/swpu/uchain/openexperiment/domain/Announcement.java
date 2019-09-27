package com.swpu.uchain.openexperiment.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author panghu
 */
@Data
public class Announcement implements Serializable {
    private Long id;

    private Long publisherId;

    private String title;

    private String content;

    private Date publishTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", publisherId=").append(publisherId);
        sb.append(", title=").append(title);
        sb.append(", content=").append(content);
        sb.append(", publishTime=").append(publishTime);
        sb.append(", updateTime=").append(updateTime);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}