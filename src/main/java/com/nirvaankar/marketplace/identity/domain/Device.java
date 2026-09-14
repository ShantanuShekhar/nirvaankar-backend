package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * One row per app install. Push notifications, forced upgrade and the
 * server-driven UI version gate all hang off {@code appVersion}, which is why
 * it is captured on every authenticated request rather than only at install.
 */
@Entity
@Getter
@Table(name = "devices")
public class Device extends BaseAuditEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "device_uuid", length = 100, nullable = false, updatable = false)
	private String deviceUuid;

	@Column(name = "platform", length = 10, nullable = false)
	private String platform;

	@Column(name = "push_token", length = 500)
	private String pushToken;

	@Column(name = "app_version", length = 20)
	private String appVersion;

	@Column(name = "os_version", length = 50)
	private String osVersion;

	@Column(name = "model", length = 50)
	private String model;

	@Column(name = "locale", length = 50)
	private String locale;

	@Column(name = "timezone", length = 50)
	private String timezone;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	/** JPA / Hibernate require an accessible no-args constructor. */
	protected Device() {
	}

	public Device(String deviceUuid, String platform, Instant lastSeenAt) {
		this.deviceUuid = deviceUuid;
		this.platform = platform;
		this.lastSeenAt = lastSeenAt;
		this.active = true;
	}

	public void attachToUser(Long userId) {
		this.userId = userId;
	}

	public void refreshRuntimeDetails(String appVersion, String osVersion, String model, String locale, String timezone,
			Instant seenAt) {
		this.appVersion = appVersion;
		this.osVersion = osVersion;
		this.model = model;
		this.locale = locale;
		this.timezone = timezone;
		this.lastSeenAt = seenAt;
	}

	public void changePushToken(String pushToken) {
		this.pushToken = pushToken;
	}

	public void deactivate() {
		this.active = false;
		this.pushToken = null;
	}

}
