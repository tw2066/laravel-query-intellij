<?php (new Illuminate\Database\Query\Builder())->from('testProject1.users')->get('<warning descr="Unknown table or view">wrongTable</warning>.<warning descr="Unknown column">wrongColumn</warning>');