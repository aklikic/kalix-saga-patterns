package com.example.wallet;

import java.util.UUID;

public class DomainGenerators {
  public static String randomCommandId() {
    return UUID.randomUUID().toString();
  }
}
