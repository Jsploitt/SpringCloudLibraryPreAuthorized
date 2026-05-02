package com.micro.userservice.models.requests;

import com.micro.userservice.models.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeStatusReq {

    private String id;
    private Status status;

}