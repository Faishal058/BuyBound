package com.buybound.catalog.controller;

import com.buybound.catalog.dto.Channel3LookupRequest;
import com.buybound.catalog.dto.Channel3SearchResponse;
import com.buybound.catalog.dto.MarketplaceResponse;
import com.buybound.catalog.service.Channel3SearchService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
  private final Channel3SearchService searchService;

  public CatalogController(Channel3SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/search")
  public Channel3SearchResponse search(
      @RequestParam String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "8") int limit) {
    return searchService.searchByQuery(q, category, null, limit);
  }

  @GetMapping("/marketplace")
  public MarketplaceResponse marketplace(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(defaultValue = "savings") String sort,
      @RequestParam(defaultValue = "50") int limit) {
    return searchService.browseMarketplace(category, q, minPrice, maxPrice, sort, limit);
  }

  @PostMapping("/lookup")
  public Channel3SearchResponse lookup(@Valid @RequestBody Channel3LookupRequest request) {
    return searchService.lookupByUrl(request.url());
  }

  /**
   * Renders a crisp warm-editorial product artwork card so catalog thumbnails never 404 offline.
   */
  @GetMapping(value = "/thumbnail", produces = "image/svg+xml")
  public ResponseEntity<String> thumbnail(
      @RequestParam(defaultValue = "Featured") String category,
      @RequestParam(defaultValue = "Product") String title) {
    String cleanCat = category.trim();
    String[] palette = categoryPalette(cleanCat);
    String safeCat = HtmlUtils.htmlEscape(cleanCat.toUpperCase(Locale.ROOT));
    String clippedTitle = title.length() > 30 ? title.substring(0, 27) + "..." : title;
    String safeTitle = HtmlUtils.htmlEscape(clippedTitle);
    String glyph = categoryGlyph(cleanCat);

    String svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 260" role="img" aria-label="%s">
          <defs>
            <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%%" stop-color="%s"/>
              <stop offset="100%%" stop-color="%s"/>
            </linearGradient>
          </defs>
          <rect width="400" height="260" rx="20" fill="url(#bg)"/>
          <circle cx="330" cy="48" r="56" fill="%s" opacity="0.14"/>
          <circle cx="68" cy="220" r="64" fill="%s" opacity="0.10"/>
          <rect x="148" y="54" width="104" height="104" rx="28" fill="#FFFFFF" stroke="#E5DFD5" stroke-width="2"/>
          <g transform="translate(176, 82)" stroke="%s" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round">
            %s
          </g>
          <rect x="24" y="182" width="128" height="22" rx="11" fill="#FFFFFF" opacity="0.88"/>
          <text x="88" y="197" text-anchor="middle" font-family="Inter,system-ui,sans-serif" font-size="10" font-weight="700" fill="%s">%s</text>
          <text x="24" y="232" font-family="Inter,system-ui,sans-serif" font-size="14" font-weight="700" fill="#292724">%s</text>
        </svg>
        """.formatted(safeTitle, palette[0], palette[1], palette[2], palette[2], palette[2], glyph, palette[2], safeCat, safeTitle);

    return ResponseEntity.ok()
        .contentType(MediaType.valueOf("image/svg+xml"))
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
        .body(svg);
  }

  private String[] categoryPalette(String category) {
    String c = category.toLowerCase(Locale.ROOT);
    if (c.contains("audio")) return new String[]{"#FBE5DB", "#FAF7F2", "#F16B50"};
    if (c.contains("phone")) return new String[]{"#EBF3FF", "#FAF7F2", "#0070BA"};
    if (c.contains("laptop")) return new String[]{"#F4EFE6", "#FAF7F2", "#292724"};
    if (c.contains("gaming")) return new String[]{"#FCE8E4", "#FAF7F2", "#D94E34"};
    if (c.contains("sneaker") || c.contains("fashion")) return new String[]{"#FDECD8", "#FAF7F2", "#D97706"};
    if (c.contains("home")) return new String[]{"#EAF6EE", "#FAF7F2", "#16A34A"};
    if (c.contains("watch")) return new String[]{"#EFEAE2", "#FAF7F2", "#5D574F"};
    if (c.contains("camera")) return new String[]{"#E8F1FA", "#FAF7F2", "#1D4ED8"};
    return new String[]{"#FBE5DB", "#FAF7F2", "#F16B50"};
  }

  private String categoryGlyph(String category) {
    String c = category.toLowerCase(Locale.ROOT);
    if (c.contains("audio")) {
      return "<path d='M6 28V20a18 18 0 0 1 36 0v8'/><rect x='2' y='26' width='10' height='16' rx='4'/><rect x='36' y='26' width='10' height='16' rx='4'/>";
    }
    if (c.contains("phone")) {
      return "<rect x='11' y='4' width='26' height='40' rx='5'/><line x1='21' y1='38' x2='27' y2='38'/>";
    }
    if (c.contains("laptop")) {
      return "<rect x='6' y='8' width='36' height='24' rx='3'/><line x1='2' y1='38' x2='46' y2='38'/>";
    }
    if (c.contains("gaming")) {
      return "<rect x='4' y='14' width='40' height='22' rx='10'/><line x1='15' y1='21' x2='15' y2='29'/><line x1='11' y1='25' x2='19' y2='25'/><circle cx='33' cy='22' r='1.5'/><circle cx='37' cy='27' r='1.5'/>";
    }
    if (c.contains("watch")) {
      return "<circle cx='24' cy='24' r='13'/><polyline points='24 17 24 24 29 27'/><path d='M17 11l1-6h12l1 6M17 37l1 6h12l1-6'/>";
    }
    if (c.contains("camera")) {
      return "<rect x='4' y='12' width='40' height='26' rx='5'/><circle cx='24' cy='25' r='8'/><path d='M15 12l3-5h12l3 5'/>";
    }
    return "<path d='M8 16h32l-3 24H11L8 16z'/><path d='M17 16V12a7 7 0 0 1 14 0v4'/>";
  }
}

