<?php
declare(strict_types=1);
namespace Athar\Infrastructure;
use PDO;
use InvalidArgumentException;
final class Database
{
    public static function connect(?string $database = null): PDO
    {
        $name = $database ?? (getenv('ATHAR_DB_NAME') ?: 'athar');
        if (!preg_match('/^[a-zA-Z0-9_]+$/', $name)) throw new InvalidArgumentException('Invalid database name.');
        $host=getenv('ATHAR_DB_HOST')?:'127.0.0.1';$port=getenv('ATHAR_DB_PORT')?:'3306';
        return new PDO("mysql:host={$host};port={$port};dbname={$name};charset=utf8mb4",getenv('ATHAR_DB_USER')?:'root',getenv('ATHAR_DB_PASSWORD')?:'',[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false,PDO::MYSQL_ATTR_INIT_COMMAND=>"SET time_zone = '+00:00'"]);
    }
}
