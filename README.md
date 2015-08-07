# check_upsc
A nagios plugin for upsc / NUT

## Example usage

### Directly calling from command line

```
java -jar check_upsc.jar -d 'NAME_OF_UPS'@'HOSTNAME_OF_NUT_SERVER' -c "ups.load=(<|60|70)|(>|30|20)"
```

### Nagios command definition

```
define command {
        command_name    check_upsc
        command_line    /usr/bin/java -jar $USER1$/check_upsc.jar -d "$ARG1$"@"$HOSTADDRESS$" -c "$ARG2$"
}
```		

## Why Scala?

I wanted to create an easy to use and correct program for checking NUT
servers. I consider shell scripts to be too error prone. Python, Perl,
etc. would have been a more or less viable option. But personal
preference decided the outcome in the end.
