# Sending Material for Analysis

If you want to send in material, which may contain security-critical data, such as

* information regarding security-related flaws which may be exploited by attackers and thus should not be made public until a proper correction is available, (responsible disclosure procedure),
* memory snapshots (Java Heap dumps), which may contain passphrases, secret keys or similar confidential data

please follow the following procedure:

1. Collect the data and pack it using a common compression format (ZIP would be preferred).
2. Use the following public key to encrypt your file:

```
-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: GnuPG v1

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
ewIn5Ogm
=EgC0
-----END PGP PUBLIC KEY BLOCK-----
```
   By doing so, we will be the only one's to open the archive.
3. Send it to the email address mentioned at the profile of [eaglerainbow](https://github.com/eaglerainbow).

We hereby assure you that we will solely use your data for improving Promregator's quality. Under no circumstances we will make the data provided by you public or send it to any third party without your consent. In case we find it necessary and appropriate to forward your data to someone else, we will first contact you to seek permission to do so. You are free to reject that permission.

In case you change your mind and want your data you have sent to us to be destroyed, please drop us a note that you want to revoke your permission for the future. Your data you have sent to us then will be destroyed at the next point in time possible.


## Organizational Comment

This is the procedure for the time being. We reserve the right to change the procedure depicted above. Expect though, that the data protection policy will be similarly rigid as stated above.
