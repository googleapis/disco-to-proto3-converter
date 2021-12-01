/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.discotoproto3converter.disco;

import com.google.cloud.discotoproto3converter.disco.CommonAcronyms.NamePieceCasingType;
import com.google.cloud.discotoproto3converter.disco.CommonAcronyms.SubNamePiece;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapted from its counterpart in gapic-generator.
 *
 * <p>Name represents an identifier name which is casing-aware.
 */
public class Name {
  private List<NamePiece> namePieces;

  /**
   * Creates a Name from a sequence of lower-underscore strings.
   *
   * @throws IllegalArgumentException if any of the strings contain any characters that are not
   *     lower case or underscores.
   */
  public static Name from(String... pieces) {
    List<NamePiece> namePieces = new ArrayList<>();
    for (String piece : pieces) {
      if (Strings.isNullOrEmpty(piece)) {
        continue;
      }
      validateLowerUnderscore(piece);
      namePieces.add(new NamePiece(piece, CaseFormat.LOWER_UNDERSCORE));
    }
    return new Name(namePieces);
  }

  /**
   * Creates a Name from a sequence of camel strings.
   *
   * @throws IllegalArgumentException if any of the strings do not follow the camel format.
   */
  public static Name anyCamel(String... pieces) {
    return camelInternal(CheckCase.NO_CHECK, AcronymMode.CAMEL_CASE, pieces);
  }

  private static CaseFormat getCamelCaseFormat(String piece) {
    if (Character.isUpperCase(piece.charAt(0))) {
      return CaseFormat.UPPER_CAMEL;
    } else {
      return CaseFormat.LOWER_CAMEL;
    }
  }

  private static Name camelInternal(
      CheckCase checkCase, AcronymMode acronymMode, String... pieces) {
    List<NamePiece> namePieces = new ArrayList<>();
    for (String piece : pieces) {
      if (Strings.isNullOrEmpty(piece)) {
        continue;
      }
      validateCamel(piece, checkCase);
      for (SubNamePiece subPiece : CommonAcronyms.splitByUpperAcronyms(piece)) {
        CaseFormat caseFormat = getCamelCaseFormat(subPiece.namePieceString());
        CasingMode casingMode = CasingMode.NORMAL;
        if (subPiece.type().equals(NamePieceCasingType.UPPER_ACRONYM)) {
          caseFormat = CaseFormat.UPPER_UNDERSCORE;
          casingMode = acronymMode.casingMode;
        }
        namePieces.add(new NamePiece(subPiece.namePieceString(), caseFormat, casingMode));
      }
    }
    return new Name(namePieces);
  }

  private static void validateLowerUnderscore(String identifier) {
    if (!isLowerUnderscore(identifier)) {
      throw new IllegalArgumentException(
          "Name: identifier not in lower-underscore: '" + identifier + "'");
    }
  }

  private static boolean isLowerUnderscore(String identifier) {
    Character underscore = Character.valueOf('_');
    for (Character ch : identifier.toCharArray()) {
      if (!Character.isLowerCase(ch) && !ch.equals(underscore) && !Character.isDigit(ch)) {
        return false;
      }
    }
    return true;
  }

  private static void validateCamel(String identifier, CheckCase check) {
    if (!isCamel(identifier, check)) {
      String casingDescription = check + " camel";
      throw new IllegalArgumentException(
          "Name: identifier not in " + casingDescription + ": '" + identifier + "'");
    }
  }

  private static boolean isCamel(String identifier, CheckCase check) {
    if (identifier.length() == 0) {
      return true;
    }
    if (!check.valid(identifier.charAt(0))) {
      return false;
    }
    for (Character ch : identifier.toCharArray()) {
      if (!Character.isLowerCase(ch) && !Character.isUpperCase(ch) && !Character.isDigit(ch)) {
        return false;
      }
    }
    return true;
  }

  private Name(List<NamePiece> namePieces) {
    this.namePieces = namePieces;
  }

  /** Returns the identifier in upper-underscore format. */
  public String toUpperUnderscore() {
    return toUnderscore(CaseFormat.UPPER_UNDERSCORE);
  }

  /** Returns the identifier in lower-underscore format. */
  public String toLowerUnderscore() {
    return toUnderscore(CaseFormat.LOWER_UNDERSCORE);
  }

  /**
   * Returns the identifier in lower-underscore format but with the first uppercase letter if the
   * original identifier started with an uppercase letter. For example ipAddress will be converted
   * to ip_address, but IpAddress will be converted to Ip_address.
   */
  public String toCapitalizedLowerUnderscore() {
    List<String> newPieces = new ArrayList<>();
    for (NamePiece namePiece : namePieces) {
      String newPiece = namePiece.caseFormat.to(CaseFormat.LOWER_UNDERSCORE, namePiece.identifier);
      if (newPieces.isEmpty() && Character.isUpperCase(namePiece.identifier.charAt(0))) {
        newPiece = Character.toUpperCase(newPiece.charAt(0)) + newPiece.substring(1);
      }
      newPieces.add(newPiece);
    }

    return Joiner.on('_').join(newPieces);
  }

  private String toUnderscore(CaseFormat caseFormat) {
    List<String> newPieces = new ArrayList<>();
    for (NamePiece namePiece : namePieces) {
      newPieces.add(namePiece.caseFormat.to(caseFormat, namePiece.identifier));
    }
    return Joiner.on('_').join(newPieces);
  }

  /** Returns the identifier in upper-camel format. */
  public String toUpperCamel() {
    return toCamel(CaseFormat.UPPER_CAMEL);
  }

  private String toCamel(CaseFormat caseFormat) {
    StringBuffer buffer = new StringBuffer();
    boolean firstPiece = true;
    for (NamePiece namePiece : namePieces) {
      if (firstPiece && caseFormat.equals(CaseFormat.LOWER_CAMEL)) {
        buffer.append(namePiece.caseFormat.to(CaseFormat.LOWER_CAMEL, namePiece.identifier));
      } else {
        CaseFormat toCaseFormat = CaseFormat.UPPER_CAMEL;
        if (namePiece.casingMode.equals(CasingMode.UPPER_CAMEL_TO_SQUASHED_UPPERCASE)) {
          toCaseFormat = CaseFormat.UPPER_UNDERSCORE;
        }
        buffer.append(namePiece.caseFormat.to(toCaseFormat, namePiece.identifier));
      }
      firstPiece = false;
    }
    return buffer.toString();
  }

  @Override
  public String toString() {
    return String.format("Name(%s)", toLowerUnderscore());
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Name) {
      return ((Name) other).toLowerUnderscore().equals(this.toLowerUnderscore());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return this.toLowerUnderscore().hashCode();
  }

  private static class NamePiece {
    public final String identifier;
    public final CaseFormat caseFormat;
    public final CasingMode casingMode;

    private NamePiece(String identifier, CaseFormat caseFormat) {
      this(identifier, caseFormat, CasingMode.NORMAL);
    }

    private NamePiece(String identifier, CaseFormat caseFormat, CasingMode casingMode) {
      this.identifier = identifier;
      this.caseFormat = caseFormat;
      this.casingMode = casingMode;
    }
  }

  // Represents how acronyms should be rendered
  private enum AcronymMode {
    CAMEL_CASE(CasingMode.NORMAL),
    UPPER_CASE(CasingMode.UPPER_CAMEL_TO_SQUASHED_UPPERCASE);

    AcronymMode(CasingMode casingMode) {
      this.casingMode = casingMode;
    }

    private final CasingMode casingMode;
  }

  // Represents overrides of desired output casing
  private enum CasingMode {
    NORMAL,
    UPPER_CAMEL_TO_SQUASHED_UPPERCASE;
  }

  private enum CheckCase {
    NO_CHECK,
    LOWER,
    UPPER;

    boolean valid(char c) {
      switch (this) {
        case NO_CHECK:
          return true;
        case UPPER:
          return Character.isUpperCase(c);
        case LOWER:
          return Character.isLowerCase(c);
      }
      throw new IllegalStateException("unreachable");
    }
  }
}
