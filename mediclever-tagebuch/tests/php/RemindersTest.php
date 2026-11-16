<?php
final class RemindersTest extends Base
{
    public function test_verschluesselung_nach_rfc_8291_laesst_sich_vom_empfaenger_lesen(): void
    {
        // Empfänger (Browser) mit eigenem Schlüsselpaar und Geheimnis.
        $ua = openssl_pkey_new(['curve_name' => 'prime256v1', 'private_key_type' => OPENSSL_KEYTYPE_EC]);
        $d = openssl_pkey_get_details($ua);
        $ua_public = "\x04" . str_pad($d['ec']['x'], 32, "\0", STR_PAD_LEFT) . str_pad($d['ec']['y'], 32, "\0", STR_PAD_LEFT);
        $auth = random_bytes(16);
        $body = MCT\Reminders::encrypt('{"title":"T","body":"Zeit für deinen Eintrag."}', $ua_public, $auth);
        $this->assertNotNull($body);
        // Entschlüsseln wie ein Browser.
        $salt = substr($body, 0, 16);
        $this->assertSame(4096, unpack('N', substr($body, 16, 4))[1]);
        $this->assertSame(65, ord($body[20]));
        $as_public = substr($body, 21, 65);
        $cipher = substr($body, 86, -16);
        $tag = substr($body, -16);
        $pem = "-----BEGIN PUBLIC KEY-----\n" . chunk_split(base64_encode(hex2bin(MCT\Reminders::SPKI_PREFIX) . $as_public), 64) . "-----END PUBLIC KEY-----\n";
        $secret = openssl_pkey_derive(openssl_pkey_get_public($pem), $ua, 32);
        $ikm = hash_hkdf('sha256', $secret, 32, "WebPush: info\0" . $ua_public . $as_public, $auth);
        $cek = hash_hkdf('sha256', $ikm, 16, "Content-Encoding: aes128gcm\0", $salt);
        $nonce = hash_hkdf('sha256', $ikm, 12, "Content-Encoding: nonce\0", $salt);
        $plain = openssl_decrypt($cipher, 'aes-128-gcm', $cek, OPENSSL_RAW_DATA, $nonce, $tag);
        $this->assertSame('{"title":"T","body":"Zeit für deinen Eintrag."}' . "\x02", $plain);
    }

    public function test_vapid_kopf_ist_ein_gueltig_signiertes_jwt(): void
    {
        $h = MCT\Reminders::vapid_header('https://fcm.googleapis.com/fcm/send/abc');
        $this->assertMatchesRegularExpression('/^vapid t=([\w-]+)\.([\w-]+)\.([\w-]+), k=([\w-]+)$/', $h);
        preg_match('/^vapid t=([\w-]+)\.([\w-]+)\.([\w-]+), k=([\w-]+)$/', $h, $m);
        $claims = json_decode(MCT\Reminders::unb64($m[2]), true);
        $this->assertSame('https://fcm.googleapis.com', $claims['aud']);
        $this->assertSame(64, strlen(MCT\Reminders::unb64($m[3])));
        $this->assertSame(65, strlen(MCT\Reminders::unb64($m[4])));
        $this->assertSame($m[4], MCT\Reminders::public_key());
    }

    public function test_nur_bekannte_push_dienste_werden_angesprochen(): void
    {
        $this->assertTrue(MCT\Reminders::host_allowed('https://fcm.googleapis.com/fcm/send/x'));
        $this->assertTrue(MCT\Reminders::host_allowed('https://updates.push.services.mozilla.com/wpush/v2/x'));
        $this->assertFalse(MCT\Reminders::host_allowed('http://fcm.googleapis.com/x'));
        $this->assertFalse(MCT\Reminders::host_allowed('https://127.0.0.1/x'));
        $this->assertFalse(MCT\Reminders::host_allowed('https://fcm.googleapis.com.boese.example/x'));
        $u = $this->user();
        $this->assertFail('bad_sub', fn() => MCT\Reminders::subscribe($u, ['endpoint' => 'https://intern.example/x', 'keys' => ['p256dh' => 'a', 'auth' => 'b']]));
    }

    public function test_faellige_erinnerungen_in_der_zeitzone_des_nutzers(): void
    {
        $u = $this->user();
        MCT\Setup::reminder_save($u, ['time' => '20:00', 'text' => 'Wie war dein Tag?']);
        MCT\Setup::reminder_save($u, ['time' => '21:00', 'text' => '', 'active' => false]);
        $goal = MCT\Goals::save($u, ['name' => 'Lesen', 'reminder_time' => '20:03', 'start_date' => '2020-01-01']);
        $tz = new DateTimeZone('Europe/Berlin');
        $today = (new DateTimeImmutable('now', $tz))->format('Y-m-d');
        $at = fn(string $t) => (new DateTimeImmutable("$today $t", $tz))->getTimestamp();
        $due = MCT\Reminders::due($u, $at('19:58'), $at('20:04'));
        $this->assertSame(['Wie war dein Tag?', 'Heute noch offen.'], array_column($due, 'text'));
        $this->assertSame([], MCT\Reminders::due($u, $at('20:04'), $at('20:09')));
        MCT\Goals::check($u, $goal['id'], $today, true);
        $this->assertCount(1, MCT\Reminders::due($u, $at('19:58'), $at('20:04')), 'erledigtes Ziel erinnert nicht mehr');
    }
}
