package test.parsing

import lexical.{TokenFactory, StringCharSource, TableDrivenScannerBuilder}
import org.scalatest._
import parsing._

class PCJsonParser extends scala.util.parsing.combinator.RegexParsers with scala.util.parsing.combinator.PackratParsers {

  private val NUMBER : Parser[Double] = """\d+(\.\d+)?""".r ^^ (_.toDouble)
  private val STRING : Parser[String] = """"(\\.|[^"])*"""".r ^^ { s => s.substring(1, s.length - 1)}
  private val BOOLEAN : Parser[Boolean] = "true|false".r ^^ (_ == "true")
  private val array : Parser[List[Any]] = "[" ~> repsep(value, ",") <~ "]"
  private val dict : Parser[Map[String, Any]] = "{" ~> repsep(STRING ~ ":" ~ value ^^ { case key ~ _ ~ value => (key, value)}, ",") <~ "}" ^^ (_.toMap)
  private val value : Parser[Any] = BOOLEAN | NUMBER | STRING | array | dict

  def parse(input : String) = {
    val result = parseAll(value, input)
    if (result.successful) result.get else throw new Exception(result.toString())
  }
}

object JsonParser {

  val ScannerBuilder = new TableDrivenScannerBuilder()
    .literals("[", "]", "{", "}", ":", ",")
    .token("WS", """\s""", _ => null)
    .token("BOOLEAN", "true|false", _ == "true")
    .token("NUMBER", """\d+(\.\d+)?""", _.toDouble)
    .token("STRING", """"(\\.|[^"])*"""", s => s.substring(1, s.length - 1))

  val Grammar = new GrammarBuilder {

    import ScannerBuilder.Implicits._
    import parsing._
    import TerminalSymbol._

    def start : INonTerminalSymbol = value

    val array : GenericNonTerminalSymbol[List[Any]] = newSymbol(
      "[" ~> opt_value_comma_list <~ "]")
    val dict : GenericNonTerminalSymbol[Map[String, Any]] = newSymbol(
      "{" ~> (opt_pair_comma_list ^^ (_.toMap)) <~ "}")
    val pair : GenericNonTerminalSymbol[(String, Any)] = newSymbol(
      "STRING" ~ ":" ~ value ^^ { case key ~ _ ~ value => (key.value.asInstanceOf[String], value)}
    )
    val opt_pair_comma_list : GenericNonTerminalSymbol[List[(String, Any)]] = newSymbol(
      EMPTY ^^ (_ => Nil) | pair_comma_list)
    val pair_comma_list : GenericNonTerminalSymbol[List[(String, Any)]] = newSymbol(
      pair ^^ (List(_))
        | pair ~ "," ~ pair_comma_list ^^ { case head ~ _ ~ tail => head :: tail}
    )
    val opt_value_comma_list : GenericNonTerminalSymbol[List[Any]] = newSymbol(
      EMPTY ^^ (_ => Nil) | value_comma_list
    )
    val value_comma_list : GenericNonTerminalSymbol[List[Any]] = newSymbol(
      value ^^ (List(_))
        | value ~ "," ~ value_comma_list ^^ { case head ~ _ ~ tail => head :: tail}
    )
    val value : GenericNonTerminalSymbol[Any] = newSymbol(
      "BOOLEAN" ^^ (_.value)
        | "NUMBER" ^^ (_.value)
        | "STRING" ^^ (_.value)
        | array
        | dict)

  }.result

}

class JsonParser(parserType : String) {
  private val parser = ParserFactory.get(parserType).create(JsonParser.Grammar)
  private val WS = JsonParser.ScannerBuilder.getToken("WS")

  def parse(input : String) : Any = {
    val scanner = JsonParser.ScannerBuilder.create(new StringCharSource(input)).filter(_ != WS)
    val result = parser.parse(scanner)
    if (parser.errors != Nil) {
      val message = parser.errors.mkString("\n")
      parser.errors = Nil
      throw new Exception(message)
    } else {
      result
    }
  }
}

class JsonParserTest extends FlatSpec with Matchers {

  val pcParser = new PCJsonParser
  val parsers = ParserFactory.get.keys.map(t => (t, new JsonParser(t)))

  val source =
    """ {"_id":"asn1","_rev":"30-166b4adeacfa3ab1d153f4e2b4466f95","name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","dist-tags":{"latest":"0.2.1"},"versions":{"0.1.0":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.0","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"engines":{"node":"~0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.0/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.0","_engineSupported":true,"_npmVersion":"1.0.15","_nodeVersion":"v0.4.9","_defaultsLoaded":true,"dist":{"shasum":"8618214ff5c0180807a885d9c1f3b67dc73e058f","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.0.tgz"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.1":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.1","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":"~0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.1/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.1","_engineSupported":true,"_npmVersion":"1.0.15","_nodeVersion":"v0.4.9","_defaultsLoaded":true,"dist":{"shasum":"a63c6cc21cafa12ac05f5c3f61e6084566292aa2","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.1.tgz"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.2":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.2","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":"~0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.2/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.2","_engineSupported":true,"_npmVersion":"1.0.18","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"ac835e89fed60d2909179f192295f36162e8c00a","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.2.tgz"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.3":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.3","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":"~0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.3/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.3","_engineSupported":true,"_npmVersion":"1.0.18","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"4bc56225d38f434d832582980bfc7f5e50bbb1c4","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.3.tgz"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.4":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.4","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":"~0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.4/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.4","_engineSupported":true,"_npmVersion":"1.0.18","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"f1ea11165f132785bc040f8d4ed8333a84bddb14","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.4.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"directories":{}},"0.1.5":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.5","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.5/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.5","_engineSupported":true,"_npmVersion":"1.0.22","_nodeVersion":"v0.5.3","_defaultsLoaded":true,"dist":{"shasum":"eb22776346f5b7583c227a2f74bb984133c83260","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.5.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"directories":{}},"0.1.6":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.6","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.6/package/package.json","serverjs":false,"contributors":false,"wscript":false},"_id":"asn1@0.1.6","_engineSupported":true,"_npmVersion":"1.0.18","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"39f634ff13d942d9d922939a2742909233b84c78","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.6.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"directories":{}},"0.1.7":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.7","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmJsonOpts":{"file":"/Users/mark/.npm/asn1/0.1.7/package/package.json","wscript":false,"contributors":false,"serverjs":false},"_id":"asn1@0.1.7","_engineSupported":true,"_npmVersion":"1.0.18","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"68f70219ea9f57f035bc40507bfb30d14f0f2f62","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.7.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"directories":{}},"0.1.8":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.8","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"_id":"asn1@0.1.8","_engineSupported":true,"_npmVersion":"1.0.104","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"9b0012d5469d70cb5516c84d4c5b772c1f1521de","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.8.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.9":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.9","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"~0.0.5"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"_id":"asn1@0.1.9","_engineSupported":true,"_npmVersion":"1.0.104","_nodeVersion":"v0.4.10","_defaultsLoaded":true,"dist":{"shasum":"d90236dce043ffa224b28f1aad4b6dfc78783a9c","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.9.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.10":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.10","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"0.1.2"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"_id":"asn1@0.1.10","_engineSupported":true,"_npmVersion":"1.0.106","_nodeVersion":"v0.4.12","_defaultsLoaded":true,"dist":{"shasum":"27488f32749567e1e117a9764c70c76b053312e5","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.10.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.1.11":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"contributors":[{"name":"David Gwynne","email":"loki@animata.net"},{"name":"Yunong Xiao","email":"yunong@joyent.com"}],"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.1.11","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","engines":{"node":">=0.4.9"},"dependencies":{},"devDependencies":{"tap":"0.1.4"},"scripts":{"pretest":"which gjslint; if [[ \"$?\" = 0 ]] ; then  gjslint --nojsdoc -r lib -r tst; else echo \"Missing gjslint. Skipping lint\"; fi","test":"./node_modules/.bin/tap ./tst"},"_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"_id":"asn1@0.1.11","_engineSupported":true,"_npmVersion":"1.1.0-beta-4","_nodeVersion":"v0.6.6","_defaultsLoaded":true,"dist":{"shasum":"559be18376d08a4ec4dbe80877d27818639b2df7","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.1.11.tgz"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.2.0":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"contributors":[{"name":"David Gwynne","email":"loki@animata.net"},{"name":"Yunong Xiao","email":"yunong@joyent.com"}],"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.2.0","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","dependencies":{},"devDependencies":{"tap":"0.4.8"},"scripts":{"test":"./node_modules/.bin/tap ./tst"},"bugs":{"url":"https://github.com/mcavage/node-asn1/issues"},"homepage":"https://github.com/mcavage/node-asn1","_id":"asn1@0.2.0","dist":{"shasum":"c38a3ddc5f6340a99ee301ad3e395472d2b0fe4e","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.2.0.tgz"},"_from":".","_npmVersion":"1.3.21","_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}},"0.2.1":{"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"contributors":[{"name":"David Gwynne","email":"loki@animata.net"},{"name":"Yunong Xiao","email":"yunong@joyent.com"}],"name":"asn1","description":"Contains parsers and serializers for ASN.1 (currently BER only)","version":"0.2.1","repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"main":"lib/index.js","dependencies":{},"devDependencies":{"tap":"0.4.8"},"scripts":{"test":"./node_modules/.bin/tap ./tst"},"bugs":{"url":"https://github.com/mcavage/node-asn1/issues"},"homepage":"https://github.com/mcavage/node-asn1","_id":"asn1@0.2.1","dist":{"shasum":"ecc73f75d31ea3c6ed9d47428db35fecc7b2c6dc","tarball":"http://registry.npmjs.org/asn1/-/asn1-0.2.1.tgz"},"_from":".","_npmVersion":"1.3.21","_npmUser":{"name":"mcavage","email":"mcavage@gmail.com"},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"directories":{}}},"maintainers":[{"name":"mcavage","email":"mcavage@gmail.com"}],"time":{"modified":"2014-04-04T20:03:39.061Z","created":"2011-07-19T19:07:06.870Z","0.1.0":"2011-12-08T16:49:17.462Z","0.1.1":"2011-12-08T16:49:17.462Z","0.1.2":"2011-12-08T16:49:17.462Z","0.1.3":"2011-12-08T16:49:17.462Z","0.1.4":"2011-12-08T16:49:17.462Z","0.1.5":"2011-12-08T16:49:17.462Z","0.1.6":"2011-12-08T16:49:17.462Z","0.1.7":"2011-12-08T16:49:17.462Z","0.1.8":"2011-11-07T22:10:26.013Z","0.1.9":"2011-11-11T19:18:29.635Z","0.1.10":"2011-12-08T16:49:17.462Z","0.1.11":"2012-01-06T17:02:19.759Z","0.2.0":"2014-02-24T17:51:55.325Z","0.2.1":"2014-04-04T20:03:39.061Z"},"author":{"name":"Mark Cavage","email":"mcavage@gmail.com"},"repository":{"type":"git","url":"git://github.com/mcavage/node-asn1.git"},"readme":"node-asn1 is a library for encoding and decoding ASN.1 datatypes in pure JS.\nCurrently BER encoding is supported; at some point I'll likely have to do DER.\n\n## Usage\n\nMostly, if you're *actually* needing to read and write ASN.1, you probably don't\nneed this readme to explain what and why.  If you have no idea what ASN.1 is,\nsee this: ftp://ftp.rsa.com/pub/pkcs/ascii/layman.asc\n\nThe source is pretty much self-explanatory, and has read/write methods for the\ncommon types out there.\n\n### Decoding\n\nThe following reads an ASN.1 sequence with a boolean.\n\n    var Ber = require('asn1').Ber;\n\n    var reader = new Ber.Reader(new Buffer([0x30, 0x03, 0x01, 0x01, 0xff]));\n\n    reader.readSequence();\n    console.log('Sequence len: ' + reader.length);\n    if (reader.peek() === Ber.Boolean)\n      console.log(reader.readBoolean());\n\n### Encoding\n\nThe following generates the same payload as above.\n\n    var Ber = require('asn1').Ber;\n\n    var writer = new Ber.Writer();\n\n    writer.startSequence();\n    writer.writeBoolean(true);\n    writer.endSequence();\n\n    console.log(writer.buffer);\n\n## Installation\n\n    npm install asn1\n\n## License\n\nMIT.\n\n## Bugs\n\nSee <https://github.com/mcavage/node-asn1/issues>.\n","readmeFilename":"README.md","homepage":"https://github.com/mcavage/node-asn1","contributors":[{"name":"David Gwynne","email":"loki@animata.net"},{"name":"Yunong Xiao","email":"yunong@joyent.com"}],"bugs":{"url":"https://github.com/mcavage/node-asn1/issues"},"_attachments":{},"_etag":"\"65BEHEHHZ8F3RWXQS4KMBKS8A\""} """

  behavior of "PCJsonParser"
  it should "Work correct" in {
    pcParser.parse("23.5") should equal(23.5)
    pcParser.parse( """ "abcd efg fds" """) should equal("abcd efg fds")
    pcParser.parse( """ [1, 2, 3] """) should equal(List(1, 2, 3))
    pcParser.parse( """ [1, {"a":1, "b":2}, 3] """) should equal(List(1, Map("a" -> 1, "b" -> 2), 3))
    pcParser.parse( """ [1, {"a":{"a":1, "b":"def"}, "b":[2,3]}, 3] """) should equal(List(1, Map("a" -> Map("a" -> 1, "b" -> "def"), "b" -> List(2, 3)), 3))
  }

  behavior of "Parsers"
  it should "Work correct" in {
    for ((_, parser) <- parsers) {
      parser.parse("23.5") should equal(23.5)
      parser.parse( """ "abcd efg fds" """) should equal("abcd efg fds")
      parser.parse( """ [1, 2, 3] """) should equal(List(1, 2, 3))
      parser.parse( """ [1, {"a":1, "b":2}, 3] """) should equal(List(1, Map("a" -> 1, "b" -> 2), 3))
      parser.parse( """ [1, {"a":{"a":1, "b":"def"}, "b":[2,3]}, 3] """) should equal(List(1, Map("a" -> Map("a" -> 1, "b" -> "def"), "b" -> List(2, 3)), 3))
    }
  }

  behavior of "Benchmark"
  it should "Work correct" in {
    val kTimes = 20
    val kLoop = 20

    println("@ Json parser benchmark\n")
    val result = pcParser.parse(source)

    utils.Profiler.measure("PC", kTimes) {
      for (_ <- 0 until kLoop) pcParser.parse(source)
    }
    for ((t, parser) <- parsers) {
      result should equal(parser.parse(source))
      utils.Profiler.measure(t, kTimes) {
        for (_ <- 0 until kLoop) parser.parse(source)
      }
    }
  }
}