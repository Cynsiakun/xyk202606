package com.cd.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuItemDTO {

    private String title;
    private String page;
    private String icon;
    private String permissionCode;
    private List<MenuItemDTO> children = new ArrayList<>();
}
