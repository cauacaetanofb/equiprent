package com.equiprent.company;

import com.equiprent.user.User;

public record CompanyAccess(User actor, Long companyId, String companyName) { }
